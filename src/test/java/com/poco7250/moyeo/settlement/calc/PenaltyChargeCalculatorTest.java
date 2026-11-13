package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PenaltyChargeCalculatorTest {

	private static final long EXPENSE_ID = 31L;
	private static final long PAYER = 1L;
	private static final long MINSU = 2L;
	private static final long YOUNGHEE = 3L;
	private static final long JISU = 4L;
	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 13, 20, 0);
	private static final LocalDateTime SECOND = FIRST.plusMinutes(10);

	private static ExpenseInput expense(long amount, long payerId) {
		return new ExpenseInput(EXPENSE_ID, amount, payerId, Set.of(1L, 2L, 3L, 4L), List.of());
	}

	private static PenaltyRuleInput fixed(long amount, LocalDateTime order, long rootId, PenalizedMember... members) {
		return new PenaltyRuleInput(EXPENSE_ID, PenaltyMoneyType.FIXED_AMOUNT, amount, null, order, rootId,
				List.of(members));
	}

	private static PenaltyRuleInput ratio(int pct, LocalDateTime order, long rootId, PenalizedMember... members) {
		return new PenaltyRuleInput(EXPENSE_ID, PenaltyMoneyType.RATIO, null, pct, order, rootId, List.of(members));
	}

	private static PenaltyRuleInput full(LocalDateTime order, long rootId, PenalizedMember... members) {
		return new PenaltyRuleInput(EXPENSE_ID, PenaltyMoneyType.FULL, null, null, order, rootId, List.of(members));
	}

	private static PenalizedMember penalized(long penaltyId, long memberId) {
		return new PenalizedMember(penaltyId, memberId);
	}

	private static PenaltyChargeResult charge(long penaltyId, long memberId, long amount) {
		return new PenaltyChargeResult(penaltyId, memberId, EXPENSE_ID, amount);
	}

	@Test
	@DisplayName("규칙이 없으면 차감 없이 지출 금액이 그대로 남는다")
	void noRules() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER), List.of());

		assertThat(deduction.charges()).isEmpty();
		assertThat(deduction.remaining()).isEqualTo(100_000L);
	}

	@Test
	@DisplayName("예 1: RATIO 30%는 100,000원의 30,000원이다")
	void ratio() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER),
				List.of(ratio(30, FIRST, 1L, penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 30_000L));
		assertThat(deduction.remaining()).isEqualTo(70_000L);
	}

	@Test
	@DisplayName("예 2: FIXED_AMOUNT는 입력 금액 그대로다")
	void fixedAmount() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER),
				List.of(fixed(20_000L, FIRST, 1L, penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 20_000L));
		assertThat(deduction.remaining()).isEqualTo(80_000L);
	}

	@Test
	@DisplayName("예 3: 고정 금액이 잔액보다 크면 잔액까지만 부담한다")
	void fixedAmountCappedByRemaining() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER),
				List.of(fixed(150_000L, FIRST, 1L, penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 100_000L));
		assertThat(deduction.remaining()).isZero();
	}

	@Test
	@DisplayName("예 4-a: FULL은 올림 없이 잔액 그대로다")
	void fullWithoutRounding() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(87_650L, PAYER),
				List.of(full(FIRST, 1L, penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 87_650L));
		assertThat(deduction.remaining()).isZero();
	}

	@Test
	@DisplayName("예 4-b: 다른 게임의 RATIO 50% 두 개는 공개 순서대로 차감하고 뒤쪽은 잔액까지만 부담한다")
	void rulesAppliedInOrder() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(87_650L, PAYER), List.of(
				ratio(50, SECOND, 2L, penalized(12L, YOUNGHEE)),
				ratio(50, FIRST, 1L, penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 43_900L), charge(12L, YOUNGHEE, 43_750L));
		assertThat(deduction.remaining()).isZero();
	}

	@Test
	@DisplayName("예 5-a: 한 규칙의 벌칙자 2명은 각자 26,300원을 부담한다")
	void eachMemberChargedSameValue() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(87_650L, PAYER),
				List.of(ratio(30, FIRST, 1L, penalized(12L, YOUNGHEE), penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 26_300L), charge(12L, YOUNGHEE, 26_300L));
		assertThat(deduction.remaining()).isEqualTo(35_050L);
	}

	@Test
	@DisplayName("예 5-b: 잔액이 모자라면 균등 분할하고 초과분은 벌칙 id가 큰 쪽에서 뺀다")
	void splitsRemainingEvenly() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(87_650L, PAYER), List.of(
				ratio(30, SECOND, 2L, penalized(11L, MINSU), penalized(12L, YOUNGHEE)),
				fixed(50_000L, FIRST, 1L, penalized(10L, JISU))));

		assertThat(deduction.charges()).containsExactly(
				charge(10L, JISU, 50_000L), charge(11L, MINSU, 18_900L), charge(12L, YOUNGHEE, 18_750L));
		assertThat(deduction.remaining()).isZero();
	}

	@Test
	@DisplayName("FULL 벌칙자 2명은 잔액을 균등 분할한다")
	void fullSplitsBetweenTwo() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(87_650L, PAYER),
				List.of(full(FIRST, 1L, penalized(11L, MINSU), penalized(12L, YOUNGHEE))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 43_900L), charge(12L, YOUNGHEE, 43_750L));
		assertThat(deduction.remaining()).isZero();
	}

	@Test
	@DisplayName("균등 분할 초과분은 벌칙자 중에 결제자가 있으면 결제자 몫에서 먼저 뺀다")
	void excessDeductedFromPayerFirst() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(87_650L, MINSU),
				List.of(full(FIRST, 1L, penalized(11L, MINSU), penalized(12L, YOUNGHEE))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, 43_750L), charge(12L, YOUNGHEE, 43_900L));
	}

	@Test
	@DisplayName("초과분이 한 사람 몫보다 크면 벌칙 id 내림차순으로 0원까지 빼고 합은 잔액과 같다")
	void excessLargerThanOneShare() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(1L, PAYER),
				List.of(full(FIRST, 1L, penalized(11L, MINSU), penalized(12L, YOUNGHEE), penalized(13L, JISU))));

		assertThat(deduction.charges()).containsExactly(
				charge(11L, MINSU, 1L), charge(12L, YOUNGHEE, 0L), charge(13L, JISU, 0L));
		assertThat(deduction.remaining()).isZero();
	}

	@Test
	@DisplayName("게임 순번이 같으면 원 회차 id가 작은 규칙을 먼저 적용한다")
	void tieBrokenByRootSessionId() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER), List.of(
				fixed(70_000L, FIRST, 9L, penalized(11L, MINSU)),
				fixed(70_000L, FIRST, 5L, penalized(12L, YOUNGHEE))));

		assertThat(deduction.charges()).containsExactly(charge(12L, YOUNGHEE, 70_000L), charge(11L, MINSU, 30_000L));
	}

	@Test
	@DisplayName("다시 돌린 회차는 원 회차의 순번과 id를 이어받아 나중에 공개돼도 원래 자리에서 적용된다")
	void rerunKeepsOriginalOrder() {
		// 민수 규칙은 원 회차(FIRST, root 1)를 다시 돌린 회차 것이라 벌칙 id는 더 크지만 순번은 앞이다
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER), List.of(
				fixed(70_000L, SECOND, 2L, penalized(12L, YOUNGHEE)),
				fixed(70_000L, FIRST, 1L, penalized(99L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(99L, MINSU, 70_000L), charge(12L, YOUNGHEE, 30_000L));
	}

	@ParameterizedTest(name = "{0}원의 {1}% = {2}원")
	@CsvSource({
			"1, 1, 1",
			"1, 100, 1",
			"100000000, 1, 1000000",
			"100000000, 100, 100000000",
			"87650, 30, 26300",
			"10000, 1, 100",
			"10001, 1, 200"
	})
	@DisplayName("RATIO 경계값: 100원 단위로 올리고 지출 금액을 넘지 않는다")
	void ratioBoundaries(long amount, int pct, long expected) {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(amount, PAYER),
				List.of(ratio(pct, FIRST, 1L, penalized(11L, MINSU))));

		assertThat(deduction.charges()).containsExactly(charge(11L, MINSU, expected));
		assertThat(deduction.remaining()).isEqualTo(amount - expected);
	}

	@Test
	@DisplayName("벌칙자가 없는 규칙은 건너뛴다")
	void skipsRuleWithoutPenalized() {
		PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense(100_000L, PAYER),
				List.of(full(FIRST, 1L)));

		assertThat(deduction.charges()).isEmpty();
		assertThat(deduction.remaining()).isEqualTo(100_000L);
	}
}
