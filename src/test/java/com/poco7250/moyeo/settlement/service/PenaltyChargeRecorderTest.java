package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.calc.PenaltyChargeResult;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PenaltyChargeRecorderTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 12, 20, 21, 0);

	@Mock
	PenaltyChargeRepository penaltyChargeRepository;

	@Captor
	ArgumentCaptor<List<PenaltyCharge>> chargesCaptor;

	private PenaltyChargeRecorder recorder;
	private SettlementRevision revision;
	private Round round;
	private Expense bowling;

	@BeforeEach
	void setUp() {
		recorder = new PenaltyChargeRecorder(penaltyChargeRepository);
		revision = SettlementRevision.create(null, 1, null, AT, false);
		round = PenaltyFixtures.round(5L, 2, "볼링장");
		bowling = PenaltyFixtures.expense(31L, round, 1L, 87_650L, "볼링");
	}

	@Test
	@DisplayName("조건 종류별 값과 대상 지출 금액·메모를 복사하고, 0원 부담액도 저장한다")
	void copiesRuleValues() {
		GameSession fixedGame = PenaltyFixtures.revealedMoneyGame(70L, round, AT);
		GameSession ratioGame = PenaltyFixtures.revealedMoneyGame(71L, round, AT.plusMinutes(1));
		GameSession fullGame = PenaltyFixtures.revealedMoneyGame(72L, round, AT.plusMinutes(2));
		Penalty fixed = applied(100L, fixedGame, 2L);
		Penalty ratio = applied(101L, ratioGame, 3L);
		Penalty full = applied(102L, fullGame, 4L);
		MeetingPenalties penalties = new MeetingPenalties(List.of(fixed, ratio, full), Map.of(
				70L, PenaltyRule.create(fixedGame, PenaltyMoneyType.FIXED_AMOUNT, 50_000L, null, bowling, 1L, AT),
				71L, PenaltyRule.create(ratioGame, PenaltyMoneyType.RATIO, null, 30, bowling, 1L, AT),
				72L, PenaltyRule.create(fullGame, PenaltyMoneyType.FULL, null, null, bowling, 1L, AT)));
		given(penaltyChargeRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

		recorder.record(revision, penalties, List.of(
				new PenaltyChargeResult(100L, 2L, 31L, 50_000L),
				new PenaltyChargeResult(101L, 3L, 31L, 26_300L),
				new PenaltyChargeResult(102L, 4L, 31L, 0L)));

		verify(penaltyChargeRepository).saveAll(chargesCaptor.capture());
		assertThat(chargesCaptor.getValue())
				.allSatisfy(charge -> {
					assertThat(charge.getRevision()).isSameAs(revision);
					assertThat(charge.getTargetExpenseId()).isEqualTo(31L);
					assertThat(charge.getTargetExpenseAmount()).isEqualTo(87_650L);
					assertThat(charge.getTargetExpenseMemo()).isEqualTo("볼링");
				})
				.extracting(charge -> charge.getPenalty().getId(), PenaltyCharge::getAmount,
						PenaltyCharge::getMoneyType, PenaltyCharge::getFixedAmount, PenaltyCharge::getRatioPct)
				.containsExactly(
						tuple(100L, 50_000L, PenaltyMoneyType.FIXED_AMOUNT, 50_000L, null),
						tuple(101L, 26_300L, PenaltyMoneyType.RATIO, null, 30),
						tuple(102L, 0L, PenaltyMoneyType.FULL, null, null));
	}

	@Test
	@DisplayName("계산 결과의 벌칙이 묶음에 없으면 조립 버그로 IllegalStateException")
	void unknownPenalty() {
		List<PenaltyChargeResult> charges = List.of(new PenaltyChargeResult(999L, 2L, 31L, 1_000L));

		assertThatThrownBy(() -> recorder.record(revision, MeetingPenalties.empty(), charges))
				.isInstanceOf(IllegalStateException.class);
	}

	private Penalty applied(Long id, GameSession game, Long memberId) {
		Penalty penalty = PenaltyFixtures.penalty(id, game, memberId);
		penalty.apply();
		return penalty;
	}
}
