package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleRequest;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;
import com.poco7250.moyeo.penalty.dto.PenaltyWaiverSnapshot;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 후 벌칙 조건 설정·변경·해제와 면제·면제 취소(PRD R-9, E2). 모임 호스트만 할 수 있다.
 * 조건과 면제는 게임 단위라 그 게임의 비무효 벌칙 전원이 함께 바뀐다.
 * 모두 금액 영향 변경이라 정산 직렬화 대상이며, 확정된 정산이면 REOPENED로 되돌리고 변경 로그를 남긴다.
 * 충돌 시 늦은 쪽은 409이고 재시도하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PenaltyConditionService {

	private final PenaltyConditionLoader penaltyConditionLoader;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final ExpenseRepository expenseRepository;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;
	private final Clock clock;

	/**
	 * 금액 조건을 정한다. 조건이 없으면 새로 만들어 전원 APPLIED로 바꾸고, 이미 있으면 값만 바꾼다.
	 * 면제 상태였다면 면제를 취소하고 조건을 건다(변경 로그 2건).
	 *
	 * @throws BusinessException 호스트가 아니면 NOT_MEETING_HOST, 조건을 걸 수 없는 게임·벌칙이면 PENALTY_NOT_CONFIGURABLE,
	 *         없는 지출이면 EXPENSE_NOT_FOUND, 다른 모임의 지출이면 EXPENSE_NOT_IN_MEETING
	 */
	@Transactional
	public PenaltyConditionResponse setRule(Long gameId, Long memberId, PenaltyRuleRequest request) {
		PenaltyConditionTarget target = penaltyConditionLoader.loadForHost(gameId, memberId);
		Expense expense = findTargetExpense(request.targetExpenseId(), target.meetingId());
		LocalDateTime now = LocalDateTime.now(clock);
		if (target.status() == PenaltyStatus.APPLIED) {
			return changeRule(target, memberId, request, expense, now);
		}
		return createRule(target, memberId, request, expense, now);
	}

	/**
	 * 금액 조건을 해제한다. 규칙을 지우고 전원 ASSIGNED로 되돌린다.
	 *
	 * @throws BusinessException 호스트가 아니면 NOT_MEETING_HOST, 조건이 걸려 있지 않으면 PENALTY_NOT_CONFIGURABLE
	 */
	@Transactional
	public PenaltyConditionResponse clearRule(Long gameId, Long memberId) {
		PenaltyConditionTarget target = penaltyConditionLoader.loadForHost(gameId, memberId);
		target.penalties().forEach(Penalty::unapply);
		reflect(target, memberId, List.of(removeRule(target)));
		return target.toResponse(null);
	}

	/**
	 * "금액 벌칙 없음"으로 전원 면제한다. 조건이 걸려 있었다면 규칙을 지우고 면제한다(변경 로그 2건).
	 *
	 * @throws BusinessException 호스트가 아니면 NOT_MEETING_HOST, 이미 면제 상태면 PENALTY_NOT_CONFIGURABLE
	 */
	@Transactional
	public PenaltyConditionResponse waive(Long gameId, Long memberId) {
		PenaltyConditionTarget target = penaltyConditionLoader.loadForHost(gameId, memberId);
		List<ConditionChange> changes = new ArrayList<>();
		if (target.status() == PenaltyStatus.APPLIED) {
			changes.add(removeRule(target));
		}
		LocalDateTime now = LocalDateTime.now(clock);
		target.penalties().forEach(penalty -> penalty.waive(memberId, now));
		changes.add(ConditionChange.ofWaiver(gameId, target.notWaivedSnapshot(), target.waivedSnapshot()));
		reflect(target, memberId, changes);
		return target.toResponse(null);
	}

	/**
	 * 면제를 취소해 전원 ASSIGNED(조건 미설정)로 되돌린다.
	 *
	 * @throws BusinessException 호스트가 아니면 NOT_MEETING_HOST, 면제 상태가 아니면 PENALTY_NOT_CONFIGURABLE
	 */
	@Transactional
	public PenaltyConditionResponse unwaive(Long gameId, Long memberId) {
		PenaltyConditionTarget target = penaltyConditionLoader.loadForHost(gameId, memberId);
		PenaltyWaiverSnapshot before = target.waivedSnapshot();
		target.penalties().forEach(Penalty::unwaive);
		reflect(target, memberId, List.of(ConditionChange.ofWaiver(gameId, before, target.notWaivedSnapshot())));
		return target.toResponse(null);
	}

	private PenaltyConditionResponse createRule(PenaltyConditionTarget target, Long memberId,
			PenaltyRuleRequest request, Expense expense, LocalDateTime now) {
		List<ConditionChange> changes = new ArrayList<>();
		if (target.status() == PenaltyStatus.WAIVED) {
			changes.add(ConditionChange.ofWaiver(target.gameId(), target.waivedSnapshot(), target.notWaivedSnapshot()));
		}
		target.penalties().forEach(Penalty::apply);
		PenaltyRule rule = penaltyRuleRepository.save(PenaltyRule.create(target.game(), request.moneyType(),
				request.amount(), request.ratioPct(), expense, memberId, now));
		changes.add(ConditionChange.ofRule(rule.getId(), ChangeType.CREATE, null, PenaltyRuleSnapshot.from(rule)));
		reflect(target, memberId, changes);
		return target.toResponse(rule);
	}

	private PenaltyConditionResponse changeRule(PenaltyConditionTarget target, Long memberId,
			PenaltyRuleRequest request, Expense expense, LocalDateTime now) {
		PenaltyRule rule = findRule(target);
		PenaltyRuleSnapshot before = PenaltyRuleSnapshot.from(rule);
		rule.change(request.moneyType(), request.amount(), request.ratioPct(), expense, memberId, now);
		reflect(target, memberId, List.of(
				ConditionChange.ofRule(rule.getId(), ChangeType.UPDATE, before, PenaltyRuleSnapshot.from(rule))));
		return target.toResponse(rule);
	}

	/**
	 * 게임의 금액 조건을 지우고, 지우기 전 값을 담은 변경을 돌려준다.
	 */
	private ConditionChange removeRule(PenaltyConditionTarget target) {
		PenaltyRule rule = findRule(target);
		PenaltyRuleSnapshot before = PenaltyRuleSnapshot.from(rule);
		penaltyRuleRepository.delete(rule);
		return ConditionChange.ofRule(rule.getId(), ChangeType.DELETE, before, null);
	}

	private PenaltyRule findRule(PenaltyConditionTarget target) {
		return penaltyRuleRepository.findByGameSessionId(target.gameId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PENALTY_NOT_CONFIGURABLE));
	}

	private Expense findTargetExpense(Long expenseId, Long meetingId) {
		Expense expense = expenseRepository.findWithRoundById(expenseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
		if (!expense.getRound().getMeeting().getId().equals(meetingId)) {
			throw new BusinessException(ErrorCode.EXPENSE_NOT_IN_MEETING);
		}
		return expense;
	}

	/**
	 * 변경을 정산에 반영하고 변경 로그를 남긴다. 로그가 변경 후 정산 상태를 담으므로 REOPENED 전이를 먼저 한다.
	 */
	private void reflect(PenaltyConditionTarget target, Long actorId, List<ConditionChange> changes) {
		settlementGuard.markAmountChanged(target.meetingId(), actorId,
				changes.stream().map(ConditionChange::summary).toList());
		changes.forEach(change -> changeLogRecorder.record(target.meetingId(), actorId, change.entityType(),
				change.entityId(), change.changeType(), change.before(), change.after()));
	}
}
