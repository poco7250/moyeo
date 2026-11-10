package com.poco7250.moyeo.settlement.service;

import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ABSENT;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ATTEND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.dto.AttendanceChange;
import com.poco7250.moyeo.settlement.dto.AttendanceSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceSettlementHandlerTest {

	private static final Long MEETING_ID = 10L;
	private static final Long ACTOR_ID = 7L;
	private static final Long MEMBER_ID = 8L;
	private static final Long ROUND_WITH_EXPENSE = 101L;
	private static final Long ROUND_WITHOUT_EXPENSE = 102L;
	private static final AttendanceChange CHANGED =
			new AttendanceChange(1L, ROUND_WITH_EXPENSE, MEMBER_ID, ATTEND, ABSENT);
	private static final AttendanceChange CHANGED_NO_EXPENSE =
			new AttendanceChange(2L, ROUND_WITHOUT_EXPENSE, MEMBER_ID, ABSENT, ATTEND);

	@Mock
	ExpenseRepository expenseRepository;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@InjectMocks
	AttendanceSettlementHandler handler;

	@Test
	@DisplayName("지출 있는 차수의 변경은 잠금·REOPENED 처리 뒤 변경 로그를 1건 남긴다")
	void changedRoundWithExpense() {
		givenRoundsWithExpense(List.of(ROUND_WITH_EXPENSE), Set.of(ROUND_WITH_EXPENSE));
		givenSettlement(confirmedSettlement());

		handler.onChanged(MEETING_ID, ACTOR_ID, List.of(CHANGED));

		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.ATTENDANCE, 1L, ChangeType.UPDATE)));
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.ATTENDANCE, 1L,
				ChangeType.UPDATE, new AttendanceSnapshot(ROUND_WITH_EXPENSE, MEMBER_ID, ATTEND),
				new AttendanceSnapshot(ROUND_WITH_EXPENSE, MEMBER_ID, ABSENT));
	}

	@Test
	@DisplayName("지출 없는 차수의 변경은 정산을 건드리지 않는다")
	void changedRoundWithoutExpense() {
		givenRoundsWithExpense(List.of(ROUND_WITHOUT_EXPENSE), Set.of());

		handler.onChanged(MEETING_ID, ACTOR_ID, List.of(CHANGED_NO_EXPENSE));

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("확정 전 변경은 직렬화하지만 변경 로그를 남기지 않는다")
	void notConfirmedYet() {
		givenRoundsWithExpense(List.of(ROUND_WITH_EXPENSE), Set.of(ROUND_WITH_EXPENSE));
		givenSettlement(Settlement.createDraft(null));

		handler.onChanged(MEETING_ID, ACTOR_ID, List.of(CHANGED));

		verify(settlementGuard).markAmountChanged(any(), any(), any());
		verify(changeLogRecorder, never()).record(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("지출 있는 차수와 없는 차수를 함께 바꾸면 있는 차수만 반영한다")
	void mixedRounds() {
		givenRoundsWithExpense(List.of(ROUND_WITH_EXPENSE, ROUND_WITHOUT_EXPENSE), Set.of(ROUND_WITH_EXPENSE));
		givenSettlement(confirmedSettlement());

		handler.onChanged(MEETING_ID, ACTOR_ID, List.of(CHANGED, CHANGED_NO_EXPENSE));

		verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.ATTENDANCE, 1L, ChangeType.UPDATE)));
		verify(changeLogRecorder).record(any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("첫 응답은 변경 전 값 없이 CREATE로 남긴다")
	void firstResponse() {
		givenRoundsWithExpense(List.of(ROUND_WITH_EXPENSE), Set.of(ROUND_WITH_EXPENSE));
		givenSettlement(confirmedSettlement());

		handler.onChanged(MEETING_ID, ACTOR_ID,
				List.of(new AttendanceChange(1L, ROUND_WITH_EXPENSE, MEMBER_ID, null, ATTEND)));

		verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.ATTENDANCE, 1L, ChangeType.CREATE)));
		verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.ATTENDANCE, 1L, ChangeType.CREATE,
				null, new AttendanceSnapshot(ROUND_WITH_EXPENSE, MEMBER_ID, ATTEND));
	}

	private void givenRoundsWithExpense(List<Long> requested, Set<Long> withExpense) {
		given(expenseRepository.findRoundIdsWithExpense(requested)).willReturn(withExpense);
	}

	private void givenSettlement(Settlement settlement) {
		given(settlementGuard.lockForAmountChange(MEETING_ID)).willReturn(settlement);
	}

	private Settlement confirmedSettlement() {
		Settlement settlement = Settlement.createDraft(null);
		settlement.confirmFirst(LocalDateTime.of(2026, 12, 20, 22, 0));
		return settlement;
	}
}
