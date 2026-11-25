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
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
	private final ExpenseReceiptService expenseReceiptService;

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
		return ExpenseResponse.of(expense, null);
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
		return ExpenseResponse.of(expense, expenseReceiptService.findResponse(expense.getId()));
	}

	/**
	 * 지출을 분배 조정·영수증과 함께 삭제한다(PRD S-1). 게임 벌칙은 지출과 무관하게 반영되므로 그대로 둔다.
	 *
	 * @return 삭제 결과
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional
	public ExpenseDeleteResponse delete(Long expenseId, Long memberId) {
		Expense expense = expenseLoader.loadForMember(expenseId, memberId);
		Long meetingId = expense.getRound().getMeeting().getId();
		settlementGuard.lockForAmountChange(meetingId);
		ExpenseSnapshot before = ExpenseSnapshot.from(expense);
		// 영수증 행도 지출을 FK로 가리키므로 먼저 지운다(파일은 커밋 뒤)
		expenseReceiptService.removeWithExpense(expense.getId());
		expenseRepository.delete(expense);
		settlementGuard.markAmountChanged(meetingId, memberId,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.EXPENSE, expense.getId(), ChangeType.DELETE)));
		changeLogRecorder.record(meetingId, memberId, ChangeLogEntityType.EXPENSE, expense.getId(), ChangeType.DELETE,
				before, null);
		return new ExpenseDeleteResponse(expense.getId());
	}

	/**
	 * 모임의 지출을 차수 순, 등록 순으로 조회한다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<ExpenseResponse> list(Long meetingId, Long memberId) {
		meetingAccessChecker.requireMember(meetingId, memberId);
		List<Expense> expenses = expenseRepository.findAllWithSharesByMeetingId(meetingId);
		Map<Long, ExpenseReceiptResponse> receipts = expenseReceiptService.findResponses(
				expenses.stream().map(Expense::getId).toList());
		return expenses.stream()
				.map(expense -> ExpenseResponse.of(expense, receipts.get(expense.getId())))
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

	private String normalizeMemo(String memo) {
		return StringUtils.hasText(memo) ? memo.strip() : null;
	}
}
