package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRD 4.5 계산 예시(예 1~5)를 벌칙 포함 정산 계산으로 검증한다.
 */
class SettlementCalculatorPenaltyTest {

	private static final long EXPENSE_ID = 31L;
	private static final long CHULSU = 1L;
	private static final long MINSU = 2L;
	private static final long YOUNGHEE = 3L;
	private static final long JISU = 4L;
	private static final long MINSU_PENALTY = 11L;
	private static final long YOUNGHEE_PENALTY = 12L;
	private static final long JISU_PENALTY = 10L;
	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 13, 20, 0);
	private static final LocalDateTime SECOND = FIRST.plusMinutes(10);
	private static final SettlementWarning PENALTY_WARNING =
			new SettlementWarning(SettlementWarningType.PENALTY_EXCEEDS_TARGET, EXPENSE_ID);

	private static SettlementResult calculate(long amount, PenaltyRuleInput... rules) {
		ExpenseInput expense = new ExpenseInput(EXPENSE_ID, amount, CHULSU, Set.of(CHULSU, MINSU, YOUNGHEE, JISU),
				List.of());
		return SettlementCalculator.calculate(new SettlementInput(List.of(expense), List.of(rules)));
	}

	private static PenaltyRuleInput rule(
			PenaltyMoneyType type, Long amount, Integer pct, LocalDateTime order, long rootId, PenalizedMember... members) {
		return new PenaltyRuleInput(EXPENSE_ID, type, amount, pct, order, rootId, List.of(members));
	}

	private static PenalizedMember minsu() {
		return new PenalizedMember(MINSU_PENALTY, MINSU);
	}

	private static PenalizedMember younghee() {
		return new PenalizedMember(YOUNGHEE_PENALTY, YOUNGHEE);
	}

	private static PenalizedMember jisu() {
		return new PenalizedMember(JISU_PENALTY, JISU);
	}

	private static PenaltyChargeResult charge(long penaltyId, long memberId, long amount) {
		return new PenaltyChargeResult(penaltyId, memberId, EXPENSE_ID, amount);
	}

	@Test
	@DisplayName("예 1: RATIO 30% — 민수 30,000 / 철수 23,200 / 영희·지수 23,400, 경고 없음")
	void example1() {
		SettlementResult result = calculate(100_000L, rule(PenaltyMoneyType.RATIO, null, 30, FIRST, 1L, minsu()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 23_200L, 76_800L),
				new MemberSettlement(MINSU, 30_000L, -30_000L),
				new MemberSettlement(YOUNGHEE, 23_400L, -23_400L),
				new MemberSettlement(JISU, 23_400L, -23_400L));
		assertThat(result.members().stream().mapToLong(MemberSettlement::shareAmount).sum()).isEqualTo(100_000L);
		assertThat(result.transfers()).containsExactly(
				new TransferGuide(MINSU, CHULSU, 30_000L),
				new TransferGuide(YOUNGHEE, CHULSU, 23_400L),
				new TransferGuide(JISU, CHULSU, 23_400L));
		assertThat(result.warnings()).isEmpty();
		assertThat(result.penaltyCharges()).containsExactly(charge(MINSU_PENALTY, MINSU, 30_000L));
	}

	@Test
	@DisplayName("예 2: FIXED_AMOUNT 20,000 — 철수 26,600 / 영희·지수 26,700")
	void example2() {
		SettlementResult result = calculate(100_000L,
				rule(PenaltyMoneyType.FIXED_AMOUNT, 20_000L, null, FIRST, 1L, minsu()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 26_600L, 73_400L),
				new MemberSettlement(MINSU, 20_000L, -20_000L),
				new MemberSettlement(YOUNGHEE, 26_700L, -26_700L),
				new MemberSettlement(JISU, 26_700L, -26_700L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 3: FIXED_AMOUNT 150,000 — 민수 100,000, 나머지 0, 경고 발생")
	void example3() {
		SettlementResult result = calculate(100_000L,
				rule(PenaltyMoneyType.FIXED_AMOUNT, 150_000L, null, FIRST, 1L, minsu()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 0L, 100_000L),
				new MemberSettlement(MINSU, 100_000L, -100_000L),
				new MemberSettlement(YOUNGHEE, 0L, 0L),
				new MemberSettlement(JISU, 0L, 0L));
		assertThat(result.transfers()).containsExactly(new TransferGuide(MINSU, CHULSU, 100_000L));
		assertThat(result.warnings()).containsExactly(PENALTY_WARNING);
	}

	@Test
	@DisplayName("예 4-a: FULL — 민수 87,650, 나머지 0, 경고 없음")
	void example4a() {
		SettlementResult result = calculate(87_650L, rule(PenaltyMoneyType.FULL, null, null, FIRST, 1L, minsu()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 0L, 87_650L),
				new MemberSettlement(MINSU, 87_650L, -87_650L),
				new MemberSettlement(YOUNGHEE, 0L, 0L),
				new MemberSettlement(JISU, 0L, 0L));
		assertThat(result.transfers()).containsExactly(new TransferGuide(MINSU, CHULSU, 87_650L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 4-b: 다른 게임의 RATIO 50% 두 개 — 민수 43,900 / 영희 43,750, 경고 없음")
	void example4b() {
		SettlementResult result = calculate(87_650L,
				rule(PenaltyMoneyType.RATIO, null, 50, SECOND, 2L, younghee()),
				rule(PenaltyMoneyType.RATIO, null, 50, FIRST, 1L, minsu()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 0L, 87_650L),
				new MemberSettlement(MINSU, 43_900L, -43_900L),
				new MemberSettlement(YOUNGHEE, 43_750L, -43_750L),
				new MemberSettlement(JISU, 0L, 0L));
		assertThat(result.penaltyCharges().stream().mapToLong(PenaltyChargeResult::amount).sum()).isEqualTo(87_650L);
		assertThat(result.transfers()).containsExactly(
				new TransferGuide(MINSU, CHULSU, 43_900L),
				new TransferGuide(YOUNGHEE, CHULSU, 43_750L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 5-a: 한 규칙 RATIO 30% 2명 — 각 26,300 / 철수 17,450 / 지수 17,600, 경고 없음")
	void example5a() {
		SettlementResult result = calculate(87_650L,
				rule(PenaltyMoneyType.RATIO, null, 30, FIRST, 1L, minsu(), younghee()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 17_450L, 70_200L),
				new MemberSettlement(MINSU, 26_300L, -26_300L),
				new MemberSettlement(YOUNGHEE, 26_300L, -26_300L),
				new MemberSettlement(JISU, 17_600L, -17_600L));
		assertThat(result.transfers()).containsExactly(
				new TransferGuide(MINSU, CHULSU, 26_300L),
				new TransferGuide(YOUNGHEE, CHULSU, 26_300L),
				new TransferGuide(JISU, CHULSU, 17_600L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 5-b: 지수 FIXED 50,000 먼저 + RATIO 30% 2명 — 지수 50,000 / 민수 18,900 / 영희 18,750 / 철수 0, 경고 발생")
	void example5b() {
		SettlementResult result = calculate(87_650L,
				rule(PenaltyMoneyType.RATIO, null, 30, SECOND, 2L, minsu(), younghee()),
				rule(PenaltyMoneyType.FIXED_AMOUNT, 50_000L, null, FIRST, 1L, jisu()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 0L, 87_650L),
				new MemberSettlement(MINSU, 18_900L, -18_900L),
				new MemberSettlement(YOUNGHEE, 18_750L, -18_750L),
				new MemberSettlement(JISU, 50_000L, -50_000L));
		assertThat(result.penaltyCharges()).containsExactly(
				charge(JISU_PENALTY, JISU, 50_000L),
				charge(MINSU_PENALTY, MINSU, 18_900L),
				charge(YOUNGHEE_PENALTY, YOUNGHEE, 18_750L));
		assertThat(result.transfers()).containsExactly(
				new TransferGuide(JISU, CHULSU, 50_000L),
				new TransferGuide(MINSU, CHULSU, 18_900L),
				new TransferGuide(YOUNGHEE, CHULSU, 18_750L));
		assertThat(result.warnings()).containsExactly(PENALTY_WARNING);
	}

	@Test
	@DisplayName("S-8: 한 게임 FULL 벌칙자 2명 — 민수 43,900 / 영희 43,750, 경고 없음")
	void fullTwoMembers() {
		SettlementResult result = calculate(87_650L,
				rule(PenaltyMoneyType.FULL, null, null, FIRST, 1L, minsu(), younghee()));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 0L, 87_650L),
				new MemberSettlement(MINSU, 43_900L, -43_900L),
				new MemberSettlement(YOUNGHEE, 43_750L, -43_750L),
				new MemberSettlement(JISU, 0L, 0L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("규칙에 없는 벌칙자(ASSIGNED·WAIVED)는 일반 참석자로 분배된다")
	void nonAppliedMemberSharesNormally() {
		// 영희는 같은 게임 벌칙자지만 APPLIED가 아니라 입력에서 빠졌다
		SettlementResult result = calculate(100_000L, rule(PenaltyMoneyType.RATIO, null, 30, FIRST, 1L, minsu()));

		assertThat(result.members()).contains(new MemberSettlement(YOUNGHEE, 23_400L, -23_400L));
		assertThat(result.penaltyCharges()).extracting(PenaltyChargeResult::memberId).containsExactly(MINSU);
	}

	@Test
	@DisplayName("앞 규칙이 잔액을 다 써서 부담액이 0원인 벌칙자도 나머지 분배에서 빠진다")
	void zeroChargedMemberStillExcluded() {
		SettlementResult result = calculate(100_000L,
				rule(PenaltyMoneyType.FIXED_AMOUNT, 100_000L, null, FIRST, 1L, minsu()),
				rule(PenaltyMoneyType.RATIO, null, 30, SECOND, 2L, younghee()));

		assertThat(result.penaltyCharges()).containsExactly(
				charge(MINSU_PENALTY, MINSU, 100_000L), charge(YOUNGHEE_PENALTY, YOUNGHEE, 0L));
		assertThat(result.members()).contains(new MemberSettlement(YOUNGHEE, 0L, 0L));
		assertThat(result.warnings()).containsExactly(PENALTY_WARNING);
	}

	@Test
	@DisplayName("벌칙은 대상 지출에만 반영되고 다른 지출은 벌칙자도 평소대로 나눈다")
	void otherExpenseUnaffected() {
		ExpenseInput target = new ExpenseInput(EXPENSE_ID, 100_000L, CHULSU, Set.of(CHULSU, MINSU), List.of());
		ExpenseInput other = new ExpenseInput(32L, 40_000L, CHULSU, Set.of(CHULSU, MINSU), List.of());

		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(target, other),
				List.of(rule(PenaltyMoneyType.FULL, null, null, FIRST, 1L, minsu()))));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 20_000L, 120_000L),
				new MemberSettlement(MINSU, 120_000L, -120_000L));
	}

	@Test
	@DisplayName("차수 참석자가 아닌 벌칙자도 벌칙 부담액을 낸다")
	void nonAttendeeStillCharged() {
		ExpenseInput expense = new ExpenseInput(EXPENSE_ID, 100_000L, CHULSU, Set.of(CHULSU, JISU), List.of());

		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(expense),
				List.of(rule(PenaltyMoneyType.FIXED_AMOUNT, 20_000L, null, FIRST, 1L, minsu()))));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(CHULSU, 40_000L, 60_000L),
				new MemberSettlement(MINSU, 20_000L, -20_000L),
				new MemberSettlement(JISU, 40_000L, -40_000L));
	}

	@Test
	@DisplayName("입력에 없는 지출을 가리키는 규칙은 예외다")
	void rejectsRuleForUnknownExpense() {
		ExpenseInput expense = new ExpenseInput(99L, 100_000L, CHULSU, Set.of(CHULSU), List.of());
		SettlementInput input = new SettlementInput(List.of(expense),
				List.of(rule(PenaltyMoneyType.FULL, null, null, FIRST, 1L, minsu())));

		assertThatThrownBy(() -> SettlementCalculator.calculate(input)).isInstanceOf(IllegalArgumentException.class);
	}
}
