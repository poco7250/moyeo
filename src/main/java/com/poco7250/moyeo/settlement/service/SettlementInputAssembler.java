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
 * DB의 지출·참석 응답을 순수 계산 입력으로 바꾼다. 벌칙 입력은 Task 035에서 여기에 추가한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementInputAssembler {

	private final ExpenseRepository expenseRepository;
	private final AttendanceRepository attendanceRepository;

	/**
	 * 모임의 정산 계산 입력을 만든다. 분배 대상은 지출이 속한 차수의 ATTEND 응답자다.
	 */
	public SettlementInput assemble(Long meetingId) {
		Map<Long, Set<Long>> attendeesByRound = attendeesByRound(meetingId);
		List<ExpenseInput> expenses = expenseRepository.findAllWithSharesByMeetingId(meetingId).stream()
				.map(expense -> toInput(expense, attendeesByRound.getOrDefault(expense.getRound().getId(), Set.of())))
				.toList();
		return new SettlementInput(expenses);
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
