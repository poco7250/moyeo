package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 금액형 게임 벌칙 반영(PRD 4.5 벌칙 정산 규칙, 2026-11-24): 벌칙자는 각자 게임 금액을 더 내고,
 * 걷힌 돈은 게임에 참가한 나머지 사람(게스트 제외)의 몫에서 똑같이 줄어든다.
 */
class SettlementCalculatorPenaltyTest {

	private static final long CHULSU = 1L;
	private static final long MINSU = 2L;
	private static final long YOUNGHEE = 3L;
	private static final long JISU = 4L;
	private static final long OUTSIDER = 5L;

	private static final ExpenseInput DINNER = new ExpenseInput(1L, 100_000L, CHULSU,
			Set.of(CHULSU, MINSU, YOUNGHEE, JISU), List.of());

	private static PenalizedMember penalized(long memberId) {
		return new PenalizedMember(memberId * 10, memberId);
	}

	@Test
	@DisplayName("4명 사다리, 민수 12,000원 꽝: 민수 +12,000, 나머지 3명 −4,000씩, 게임 안 한 사람은 그대로")
	void splitsAmongOtherParticipants() {
		ExpenseInput dinner = new ExpenseInput(1L, 100_000L, CHULSU,
				Set.of(CHULSU, MINSU, YOUNGHEE, JISU, OUTSIDER), List.of());
		GamePenaltyInput ladder = new GamePenaltyInput(7L, 12_000L, List.of(penalized(MINSU)),
				Set.of(CHULSU, YOUNGHEE, JISU));

		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(dinner), List.of(ladder)));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 16_000L, 84_000L),
				new MemberSettlement(MINSU, 32_000L, -32_000L),
				new MemberSettlement(YOUNGHEE, 16_000L, -16_000L),
				new MemberSettlement(JISU, 16_000L, -16_000L),
				new MemberSettlement(OUTSIDER, 20_000L, -20_000L));
		assertThat(result.penaltyCharges()).containsExactly(new PenaltyChargeResult(20L, MINSU, 7L, 12_000L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("나눠떨어지지 않으면 memberId가 작은 사람부터 1원씩 더 줄인다")
	void spreadsRemainderByMemberId() {
		Map<Long, Long> shares = new HashMap<>();
		GamePenaltyInput game = new GamePenaltyInput(7L, 10_000L, List.of(penalized(MINSU)),
				Set.of(JISU, CHULSU, YOUNGHEE));

		GamePenaltyAdjuster.apply(game, shares);

		assertThat(shares).containsExactlyInAnyOrderEntriesOf(Map.of(
				MINSU, 10_000L, CHULSU, -3_334L, YOUNGHEE, -3_333L, JISU, -3_333L));
	}

	@Test
	@DisplayName("벌칙자 2명이면 각자 금액을 내고 합계를 나눠 받는다")
	void multiplePenalized() {
		Map<Long, Long> shares = new HashMap<>();
		GamePenaltyInput game = new GamePenaltyInput(7L, 5_000L, List.of(penalized(MINSU), penalized(JISU)),
				Set.of(CHULSU, YOUNGHEE));

		List<PenaltyChargeResult> charges = GamePenaltyAdjuster.apply(game, shares);

		assertThat(shares).containsExactlyInAnyOrderEntriesOf(Map.of(
				MINSU, 5_000L, JISU, 5_000L, CHULSU, -5_000L, YOUNGHEE, -5_000L));
		assertThat(charges).extracting(PenaltyChargeResult::amount).containsExactly(5_000L, 5_000L);
	}

	@Test
	@DisplayName("나눠 받을 사람이 없으면(나머지가 전부 게스트) 아무도 안 내고 부담액 0원으로 남긴다")
	void noCreditedMembers() {
		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(DINNER),
				List.of(new GamePenaltyInput(7L, 10_000L, List.of(penalized(MINSU)), Set.of()))));

		assertThat(result.members()).extracting(MemberSettlement::shareAmount)
				.containsExactly(25_000L, 25_000L, 25_000L, 25_000L);
		assertThat(result.penaltyCharges()).containsExactly(new PenaltyChargeResult(20L, MINSU, 7L, 0L));
	}

	@Test
	@DisplayName("지출이 없어도 반영되고, 나눠 받는 사람은 순잔액이 양수가 돼 벌칙자에게 받는다")
	void appliesWithoutExpenses() {
		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(),
				List.of(new GamePenaltyInput(7L, 10_000L, List.of(penalized(MINSU)), Set.of(CHULSU, JISU)))));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, -5_000L, 5_000L),
				new MemberSettlement(MINSU, 10_000L, -10_000L),
				new MemberSettlement(JISU, -5_000L, 5_000L));
		assertThat(result.transfers()).containsExactlyInAnyOrder(
				new TransferGuide(MINSU, CHULSU, 5_000L),
				new TransferGuide(MINSU, JISU, 5_000L));
	}

	@Test
	@DisplayName("벌칙자가 나눠 받는 사람에 섞이면 조립 버그라 예외다")
	void rejectsOverlap() {
		GamePenaltyInput game = new GamePenaltyInput(7L, 10_000L, List.of(penalized(MINSU)), Set.of(MINSU, JISU));

		assertThatThrownBy(() -> GamePenaltyAdjuster.apply(game, new HashMap<>()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
