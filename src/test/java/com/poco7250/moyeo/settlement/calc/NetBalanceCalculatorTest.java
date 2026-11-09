package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetBalanceCalculatorTest {

	private static ExpenseInput expense(long expenseId, long amount, long payerId) {
		return new ExpenseInput(expenseId, amount, payerId, Set.of(), List.of());
	}

	@Test
	@DisplayName("여러 지출·여러 결제자의 순잔액은 결제액 − 부담액이고 합은 0이다")
	void calculatesAcrossExpenses() {
		List<MemberSettlement> members = NetBalanceCalculator.calculate(
				List.of(expense(1L, 60_000L, 1L), expense(2L, 30_000L, 2L)),
				Map.of(1L, 30_000L, 2L, 30_000L, 3L, 30_000L));

		assertThat(members).containsExactly(
				new MemberSettlement(1L, 30_000L, 30_000L),
				new MemberSettlement(2L, 30_000L, 0L),
				new MemberSettlement(3L, 30_000L, -30_000L));
	}

	@Test
	@DisplayName("분배 대상이 아닌 결제자도 부담액 0원으로 결과에 포함된다")
	void includesAbsentPayer() {
		List<MemberSettlement> members = NetBalanceCalculator.calculate(
				List.of(expense(1L, 20_000L, 9L)),
				Map.of(1L, 20_000L));

		assertThat(members).containsExactly(
				new MemberSettlement(1L, 20_000L, -20_000L),
				new MemberSettlement(9L, 0L, 20_000L));
	}

	@Test
	@DisplayName("부담액 합이 지출 합과 다르면 예외가 난다")
	void rejectsUnbalancedShares() {
		assertThatThrownBy(() -> NetBalanceCalculator.calculate(
				List.of(expense(1L, 20_000L, 1L)),
				Map.of(1L, 10_000L)))
				.isInstanceOf(IllegalStateException.class);
	}
}
