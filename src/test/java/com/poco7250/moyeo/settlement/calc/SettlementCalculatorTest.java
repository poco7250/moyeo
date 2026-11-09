package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

	private static final long MEMBER_COUNT = 6L;
	private static final long MAX_AMOUNT = 100_000_000L;

	@Test
	@DisplayName("S-2: 참석자 4명 100,000원은 각자 25,000원이고 불참자는 결과에 없다")
	void splitsAmongAttendees() {
		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(
				new ExpenseInput(1L, 100_000L, 1L, Set.of(1L, 2L, 3L, 4L), List.of()))));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(1L, 25_000L, 75_000L),
				new MemberSettlement(2L, 25_000L, -25_000L),
				new MemberSettlement(3L, 25_000L, -25_000L),
				new MemberSettlement(4L, 25_000L, -25_000L));
		assertThat(result.transfers()).hasSize(3);
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("S-3: 제외자는 0원이고 나머지 3명이 나눈다")
	void excludesMember() {
		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(
				new ExpenseInput(1L, 100_000L, 1L, Set.of(1L, 2L, 3L, 4L),
						List.of(new ShareAdjustment(4L, true, null, null))))));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(1L, 33_200L, 66_800L),
				new MemberSettlement(2L, 33_400L, -33_400L),
				new MemberSettlement(3L, 33_400L, -33_400L),
				new MemberSettlement(4L, 0L, 0L));
		assertThat(result.transfers()).containsExactly(
				new TransferGuide(2L, 1L, 33_400L),
				new TransferGuide(3L, 1L, 33_400L));
	}

	@Test
	@DisplayName("여러 지출의 부담액을 합쳐 경고와 함께 돌려준다")
	void mergesExpenses() {
		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of(
				new ExpenseInput(1L, 40_000L, 1L, Set.of(1L, 2L), List.of()),
				new ExpenseInput(2L, 10_000L, 2L, Set.of(), List.of()))));

		assertThat(result.members()).containsExactly(
				new MemberSettlement(1L, 20_000L, 20_000L),
				new MemberSettlement(2L, 30_000L, -20_000L));
		assertThat(result.transfers()).containsExactly(new TransferGuide(2L, 1L, 20_000L));
		assertThat(result.warnings()).containsExactly(
				new SettlementWarning(SettlementWarningType.NO_SHARE_TARGET, 2L));
	}

	@Test
	@DisplayName("지출이 없으면 빈 결과다")
	void returnsEmptyWithoutExpenses() {
		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(List.of()));

		assertThat(result.members()).isEmpty();
		assertThat(result.transfers()).isEmpty();
		assertThat(result.warnings()).isEmpty();
	}

	@RepeatedTest(200)
	@DisplayName("무작위 입력에서도 부담액 합 = 지출 합, 부담액 ≥ 0, 순잔액 합 0, 송금 후 전원 0이다")
	void keepsInvariants(RepetitionInfo info) {
		Random random = new Random(info.getCurrentRepetition());
		List<ExpenseInput> expenses = new ArrayList<>();
		int expenseCount = random.nextInt(1, 6);
		for (long expenseId = 1; expenseId <= expenseCount; expenseId++) {
			expenses.add(randomExpense(random, expenseId));
		}

		SettlementResult result = SettlementCalculator.calculate(new SettlementInput(expenses));

		long totalAmount = expenses.stream().mapToLong(ExpenseInput::amount).sum();
		assertThat(result.members().stream().mapToLong(MemberSettlement::shareAmount).sum()).isEqualTo(totalAmount);
		assertThat(result.members()).allSatisfy(member -> assertThat(member.shareAmount()).isNotNegative());
		assertThat(result.members().stream().mapToLong(MemberSettlement::netBalance).sum()).isZero();

		long nonZero = result.members().stream().filter(member -> member.netBalance() != 0L).count();
		assertThat((long) result.transfers().size()).isLessThanOrEqualTo(Math.max(nonZero - 1, 0L));
		assertThat(balancesAfterTransfers(result).values()).allMatch(balance -> balance == 0L);
	}

	private static ExpenseInput randomExpense(Random random, long expenseId) {
		long amount = random.nextBoolean() ? random.nextLong(1L, 1_000L) : random.nextLong(1L, MAX_AMOUNT + 1);
		long payerId = random.nextLong(1L, MEMBER_COUNT + 1);
		Set<Long> attendees = new HashSet<>();
		List<ShareAdjustment> adjustments = new ArrayList<>();
		for (long memberId = 1; memberId <= MEMBER_COUNT; memberId++) {
			if (random.nextInt(4) != 0) {
				attendees.add(memberId);
			}
			randomAdjustment(random, memberId, amount).ifPresent(adjustments::add);
		}
		return new ExpenseInput(expenseId, amount, payerId, attendees, adjustments);
	}

	private static Optional<ShareAdjustment> randomAdjustment(Random random, long memberId, long amount) {
		return switch (random.nextInt(5)) {
			case 0 -> Optional.of(new ShareAdjustment(memberId, true, null, null));
			case 1 -> Optional.of(new ShareAdjustment(memberId, false, random.nextLong(1L, amount + 1), null));
			case 2 -> Optional.of(new ShareAdjustment(memberId, false, null, random.nextInt(1, 101)));
			default -> Optional.empty();
		};
	}

	private static Map<Long, Long> balancesAfterTransfers(SettlementResult result) {
		Map<Long, Long> balances = new HashMap<>();
		result.members().forEach(member -> balances.put(member.memberId(), member.netBalance()));
		for (TransferGuide transfer : result.transfers()) {
			balances.merge(transfer.fromMemberId(), transfer.amount(), Long::sum);
			balances.merge(transfer.toMemberId(), -transfer.amount(), Long::sum);
		}
		return balances;
	}
}
