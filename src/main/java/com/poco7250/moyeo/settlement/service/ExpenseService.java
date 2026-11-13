package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.ExpenseDeleteResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 지출 등록·수정·삭제·조회(PRD S-1, S-11). 쓰기는 정산 직렬화 대상이며, 확정된 정산이면 REOPENED로 되돌리고 변경 로그를 남긴다.
 * 충돌 시 늦은 쪽은 409이고 재시도하지 않는다(PRD 6장 재시도 대상 아님).
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final ExpenseLoader expenseLoader;
	private final ExpenseRepository expenseRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;
	private final TargetExpenseRuleDetacher targetExpenseRuleDetacher;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 차수에 지출을 등록한다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 결제자가 그룹 멤버가 아니면 INVALID_INPUT
	 */
	@Transactional
	public ExpenseResponse create(Long roundId, Long memberId, ExpenseRequest request) {
		Round round = meetingAccessChecker.requireRoundMember(roundId, memberId);
		Meeting meeting = round.getMeeting();
		settlementGuard.lockForAmountChange(meeting.getId());
		Member payer = findPayer(meeting, request.payerId());
		Expense expense = expenseRepository.save(
				Expense.create(round, payer, request.amount(), normalizeMemo(request.memo())));
		recordChange(meeting, memberId, expense, ChangeType.CREATE, null, ExpenseSnapshot.from(expense));
		return ExpenseResponse.from(expense);
	}

	/**
	 * 지출의 금액·결제자·메모를 수정한다. 분배 조정은 유지한다.
	 *
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER,
	 *         결제자가 그룹 멤버가 아니면 INVALID_INPUT
	 */
	@Transactional
	public ExpenseResponse update(Long expenseId, Long memberId, ExpenseRequest request) {
		Expense expense = expenseLoader.loadForMember(expenseId, memberId);
		Meeting meeting = expense.getRound().getMeeting();
		settlementGuard.lockForAmountChange(meeting.getId());
		Member payer = findPayer(meeting, request.payerId());
		ExpenseSnapshot before = ExpenseSnapshot.from(expense);
		expense.update(payer, request.amount(), normalizeMemo(request.memo()));
		recordChange(meeting, memberId, expense, ChangeType.UPDATE, before, ExpenseSnapshot.from(expense));
		return ExpenseResponse.from(expense);
	}

	/**
	 * 지출을 분배 조정과 함께 삭제한다. 이 지출을 대상으로 한 벌칙 조건도 같은 트랜잭션에서 지우고
	 * 그 게임 벌칙을 조건 미설정(ASSIGNED)으로 되돌린다(PRD S-1).
	 *
	 * @return 조건이 지워진 게임 목록을 담은 삭제 결과
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional
	public ExpenseDeleteResponse delete(Long expenseId, Long memberId) {
		Expense expense = expenseLoader.loadForMember(expenseId, memberId);
		Long meetingId = expense.getRound().getMeeting().getId();
		settlementGuard.lockForAmountChange(meetingId);
		ExpenseSnapshot before = ExpenseSnapshot.from(expense);
		// 조건이 지출을 FK로 가리키므로 조건 삭제가 지출 삭제보다 먼저 쓰기 대기열에 들어가야 한다
		List<DetachedRule> detached = targetExpenseRuleDetacher.detach(expense.getId());
		expenseRepository.delete(expense);
		recordDeletion(meetingId, memberId, expense.getId(), before, detached);

		ExpenseDeleteResponse response = new ExpenseDeleteResponse(expense.getId(),
				detached.stream().map(DetachedRule::gameId).toList());
		if (!detached.isEmpty()) {
			eventPublisher.publishEvent(new PenaltyConditionResetEvent(meetingId, response));
		}
		return response;
	}

	/**
	 * 모임의 지출을 차수 순, 등록 순으로 조회한다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<ExpenseResponse> list(Long meetingId, Long memberId) {
		meetingAccessChecker.requireMember(meetingId, memberId);
		return expenseRepository.findAllWithSharesByMeetingId(meetingId).stream()
				.map(ExpenseResponse::from)
				.toList();
	}

	private Member findPayer(Meeting meeting, Long payerId) {
		return groupMemberRepository.findByGroupIdAndMemberId(meeting.getGroup().getId(), payerId)
				.map(GroupMember::getMember)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
	}

	private void recordChange(Meeting meeting, Long actorId, Expense expense, ChangeType changeType,
			ExpenseSnapshot before, ExpenseSnapshot after) {
		settlementGuard.markAmountChanged(meeting.getId(), actorId,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.EXPENSE, expense.getId(), changeType)));
		changeLogRecorder.record(meeting.getId(), actorId, ChangeLogEntityType.EXPENSE, expense.getId(), changeType,
				before, after);
	}

	/**
	 * 지출 삭제와 함께 지운 조건을 정산에 한 번 반영하고, 지출 1건 + 조건 n건(삭제 전 값)을 변경 로그에 남긴다.
	 */
	private void recordDeletion(Long meetingId, Long actorId, Long expenseId, ExpenseSnapshot before,
			List<DetachedRule> detached) {
		List<SettlementChangeSummary> changes = new ArrayList<>();
		changes.add(new SettlementChangeSummary(ChangeLogEntityType.EXPENSE, expenseId, ChangeType.DELETE));
		detached.forEach(rule -> changes.add(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, rule.ruleId(), ChangeType.DELETE)));
		settlementGuard.markAmountChanged(meetingId, actorId, changes);
		changeLogRecorder.record(meetingId, actorId, ChangeLogEntityType.EXPENSE, expenseId, ChangeType.DELETE,
				before, null);
		detached.forEach(rule -> changeLogRecorder.record(meetingId, actorId, ChangeLogEntityType.PENALTY_RULE,
				rule.ruleId(), ChangeType.DELETE, rule.before(), null));
	}

	private String normalizeMemo(String memo) {
		return StringUtils.hasText(memo) ? memo.strip() : null;
	}
}
