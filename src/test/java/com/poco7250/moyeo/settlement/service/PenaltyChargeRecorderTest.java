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
import com.poco7250.moyeo.settlement.calc.PenaltyChargeResult;
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

	@BeforeEach
	void setUp() {
		recorder = new PenaltyChargeRecorder(penaltyChargeRepository);
		revision = SettlementRevision.create(null, 1, null, AT, false);
		round = PenaltyFixtures.round(5L, 2, "볼링장");
	}

	@Test
	@DisplayName("게임 금액을 고정 금액으로 복사하고 대상 지출 값은 비우며, 0원 부담액도 저장한다")
	void copiesGameAmount() {
		GameSession tenThousand = PenaltyFixtures.revealedMoneyGame(70L, round, AT, 10_000L);
		GameSession fiveThousand = PenaltyFixtures.revealedMoneyGame(71L, round, AT.plusMinutes(1), 5_000L);
		Penalty first = PenaltyFixtures.penalty(100L, tenThousand, 2L);
		Penalty second = PenaltyFixtures.penalty(101L, fiveThousand, 3L);
		MeetingPenalties penalties = new MeetingPenalties(List.of(first, second), Map.of());
		given(penaltyChargeRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

		recorder.record(revision, penalties, List.of(
				new PenaltyChargeResult(100L, 2L, 70L, 10_000L),
				new PenaltyChargeResult(101L, 3L, 71L, 0L)));

		verify(penaltyChargeRepository).saveAll(chargesCaptor.capture());
		assertThat(chargesCaptor.getValue())
				.allSatisfy(charge -> {
					assertThat(charge.getRevision()).isSameAs(revision);
					assertThat(charge.getMoneyType()).isEqualTo(PenaltyMoneyType.FIXED_AMOUNT);
					assertThat(charge.getTargetExpenseId()).isNull();
					assertThat(charge.getRatioPct()).isNull();
				})
				.extracting(charge -> charge.getPenalty().getId(), PenaltyCharge::getAmount,
						PenaltyCharge::getFixedAmount)
				.containsExactly(tuple(100L, 10_000L, 10_000L), tuple(101L, 0L, 5_000L));
	}

	@Test
	@DisplayName("계산 결과의 벌칙이 묶음에 없으면 조립 버그로 IllegalStateException")
	void unknownPenalty() {
		List<PenaltyChargeResult> charges = List.of(new PenaltyChargeResult(999L, 2L, 70L, 1_000L));

		assertThatThrownBy(() -> recorder.record(revision, MeetingPenalties.empty(), charges))
				.isInstanceOf(IllegalStateException.class);
	}
}
