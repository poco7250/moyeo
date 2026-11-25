package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.SettlementInput;
import com.poco7250.moyeo.settlement.calc.ShareAdjustment;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.ExpenseShare;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.util.List;
import java.util.Map;
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
	 * 벌칙은 금액이 정해진 게임의 그룹 멤버 APPLIED 벌칙만 넣는다(WAIVED·게스트 벌칙은 표시만).
	 */
	public SettlementInput assemble(Long meetingId, MeetingPenalties penalties) {
		Map<Long, Set<Long>> attendeesByRound = attendeesByRound(meetingId);
		List<ExpenseInput> expenses = expenseRepository.findAllWithSharesByMeetingId(meetingId).stream()
				.map(expense -> toInput(expense, attendeesByRound.getOrDefault(expense.getRound().getId(), Set.of())))
				.toList();
		return new SettlementInput(expenses, penalties.toGameInputs());
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
}
