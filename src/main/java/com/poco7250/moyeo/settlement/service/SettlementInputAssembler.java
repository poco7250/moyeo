package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.PenalizedMember;
import com.poco7250.moyeo.settlement.calc.PenaltyRuleInput;
import com.poco7250.moyeo.settlement.calc.SettlementInput;
import com.poco7250.moyeo.settlement.calc.ShareAdjustment;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.ExpenseShare;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DB의 지출·참석 응답·벌칙을 순수 계산 입력으로 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class SettlementInputAssembler {

	private final ExpenseRepository expenseRepository;
	private final AttendanceRepository attendanceRepository;
	private final MeetingPenaltyLoader meetingPenaltyLoader;

	/**
	 * 모임의 정산 계산 입력을 만든다. 벌칙도 직접 읽는다.
	 */
	public SettlementInput assemble(Long meetingId) {
		return assemble(meetingId, meetingPenaltyLoader.load(meetingId));
	}

	/**
	 * 이미 읽은 벌칙 묶음으로 모임의 정산 계산 입력을 만든다. 분배 대상은 지출이 속한 차수의 ATTEND 응답자이고,
	 * 벌칙은 APPLIED 벌칙자만 넣는다(ASSIGNED·WAIVED는 일반 참석자로 분배).
	 */
	public SettlementInput assemble(Long meetingId, MeetingPenalties penalties) {
		Map<Long, Set<Long>> attendeesByRound = attendeesByRound(meetingId);
		List<ExpenseInput> expenses = expenseRepository.findAllWithSharesByMeetingId(meetingId).stream()
				.map(expense -> toInput(expense, attendeesByRound.getOrDefault(expense.getRound().getId(), Set.of())))
				.toList();
		return new SettlementInput(expenses, toRuleInputs(penalties));
	}

	private Map<Long, Set<Long>> attendeesByRound(Long meetingId) {
		return attendanceRepository.findAllByMeetingId(meetingId).stream()
				.filter(attendance -> attendance.getStatus() == AttendanceStatus.ATTEND)
				.collect(Collectors.groupingBy(
						attendance -> attendance.getRound().getId(),
						Collectors.mapping(attendance -> attendance.getMember().getId(), Collectors.toSet())));
	}

	private ExpenseInput toInput(Expense expense, Set<Long> attendeeIds) {
		List<ShareAdjustment> adjustments = expense.getShares().stream().map(this::toAdjustment).toList();
		return new ExpenseInput(expense.getId(), expense.getAmount(), expense.getPayer().getId(), attendeeIds,
				adjustments);
	}

	private ShareAdjustment toAdjustment(ExpenseShare share) {
		return new ShareAdjustment(share.getMember().getId(), share.isExcluded(), share.getAmount(), share.getWeight());
	}

	/**
	 * APPLIED 벌칙을 게임별로 묶어(조회 순서 = 게임 순번 순 유지) 규칙 입력으로 바꾼다.
	 */
	private List<PenaltyRuleInput> toRuleInputs(MeetingPenalties penalties) {
		Map<Long, List<Penalty>> appliedByGameId = penalties.penalties().stream()
				.filter(penalty -> penalty.getStatus() == PenaltyStatus.APPLIED)
				.collect(Collectors.groupingBy(penalty -> penalty.getGameSession().getId(), LinkedHashMap::new,
						Collectors.toList()));
		return appliedByGameId.values().stream()
				.flatMap(applied -> toRuleInput(applied, penalties).stream())
				.toList();
	}

	private Optional<PenaltyRuleInput> toRuleInput(List<Penalty> applied, MeetingPenalties penalties) {
		GameSession game = applied.getFirst().getGameSession();
		return penalties.ruleOf(game.getId()).map(rule -> toRuleInput(game, applied, rule));
	}

	private PenaltyRuleInput toRuleInput(GameSession game, List<Penalty> applied, PenaltyRule rule) {
		List<PenalizedMember> penalized = applied.stream()
				.map(penalty -> new PenalizedMember(penalty.getId(), penalty.getMember().getId()))
				.toList();
		return new PenaltyRuleInput(rule.getTargetExpense().getId(), rule.getMoneyType(), rule.getAmount(),
				rule.getRatioPct(), game.getApplyOrderAt(), game.getRootSessionId(), penalized);
	}
}
