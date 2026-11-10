package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.dto.AttendanceChange;
import com.poco7250.moyeo.settlement.dto.AttendanceSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참석 변경을 정산에 반영한다(PRD 4.5, S-11). 지출 있는 차수의 변경만 금액 영향 변경으로 보고,
 * 지출 없는 차수의 변경은 응답이 몰릴 때 충돌이 늘지 않도록 직렬화에서 뺀다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AttendanceSettlementHandler {

	private final ExpenseRepository expenseRepository;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;

	/**
	 * 참석 변경 목록 중 지출 있는 차수의 변경을 정산에 반영한다. 변경 로그는 정산이 한 번이라도 확정된 뒤부터 남긴다.
	 *
	 * @param actorId 변경한 회원(호스트 대리 수정이면 호스트)
	 * @param changes 실제로 값이 달라진 참석 응답
	 */
	public void onChanged(Long meetingId, Long actorId, List<AttendanceChange> changes) {
		List<AttendanceChange> amountChanges = filterRoundsWithExpense(changes);
		if (amountChanges.isEmpty()) {
			return;
		}
		Settlement settlement = settlementGuard.lockForAmountChange(meetingId);
		settlementGuard.markAmountChanged(meetingId, actorId, amountChanges.stream().map(this::toSummary).toList());
		if (settlement.hasBeenConfirmed()) {
			amountChanges.forEach(change -> record(meetingId, actorId, change));
		}
	}

	private List<AttendanceChange> filterRoundsWithExpense(List<AttendanceChange> changes) {
		Set<Long> roundIdsWithExpense = expenseRepository.findRoundIdsWithExpense(
				changes.stream().map(AttendanceChange::roundId).toList());
		return changes.stream().filter(change -> roundIdsWithExpense.contains(change.roundId())).toList();
	}

	private SettlementChangeSummary toSummary(AttendanceChange change) {
		return new SettlementChangeSummary(ChangeLogEntityType.ATTENDANCE, change.attendanceId(), changeType(change));
	}

	private void record(Long meetingId, Long actorId, AttendanceChange change) {
		AttendanceSnapshot before = change.before() == null
				? null
				: new AttendanceSnapshot(change.roundId(), change.memberId(), change.before());
		AttendanceSnapshot after = new AttendanceSnapshot(change.roundId(), change.memberId(), change.after());
		changeLogRecorder.record(meetingId, actorId, ChangeLogEntityType.ATTENDANCE, change.attendanceId(),
				changeType(change), before, after);
	}

	private ChangeType changeType(AttendanceChange change) {
		return change.before() == null ? ChangeType.CREATE : ChangeType.UPDATE;
	}
}
