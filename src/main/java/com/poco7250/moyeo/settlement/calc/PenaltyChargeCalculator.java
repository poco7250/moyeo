package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 지출 1건에 걸린 벌칙 부담액을 계산한다(PRD 4.5 벌칙 정산 규칙, S-8).
 * 규칙을 게임 순번대로 잔액에서 차감하고, 같은 규칙의 벌칙자는 잔액이 모자라면 균등 분할한다.
 */
public final class PenaltyChargeCalculator {

	private static final long ROUND_UNIT = 100L;
	private static final long RATIO_ROUND_UP = 9_999L;
	private static final long RATIO_DIVISOR = 10_000L;

	private static final Comparator<PenaltyRuleInput> APPLY_ORDER =
			Comparator.comparing(PenaltyRuleInput::applyOrderAt).thenComparingLong(PenaltyRuleInput::rootSessionId);

	private PenaltyChargeCalculator() {
	}

	/**
	 * 지출을 대상으로 한 규칙들의 벌칙별 부담액과 남은 금액을 계산한다. 부담액 합은 지출 금액을 넘지 않는다.
	 *
	 * @param rules 이 지출을 대상으로 한 규칙. 순서는 상관없다
	 */
	public static PenaltyDeduction calculate(ExpenseInput expense, List<PenaltyRuleInput> rules) {
		List<PenaltyChargeResult> charges = new ArrayList<>();
		long remaining = expense.amount();
		for (PenaltyRuleInput rule : rules.stream().sorted(APPLY_ORDER).toList()) {
			if (rule.penalized().isEmpty()) {
				continue;
			}
			Map<Long, Long> amounts = chargeRule(expense, rule, remaining);
			for (PenalizedMember member : penaltyIdOrder(rule)) {
				long amount = amounts.get(member.penaltyId());
				charges.add(new PenaltyChargeResult(member.penaltyId(), member.memberId(), expense.expenseId(), amount));
				remaining -= amount;
			}
		}
		return new PenaltyDeduction(charges, remaining);
	}

	// 규칙 하나의 벌칙 id별 부담액. 각자 계산값의 합이 잔액을 넘으면 잔액을 균등 분할한다(D3)
	private static Map<Long, Long> chargeRule(ExpenseInput expense, PenaltyRuleInput rule, long remaining) {
		long count = rule.penalized().size();
		long value = perMemberValue(rule, expense.amount(), remaining);
		Map<Long, Long> amounts = new TreeMap<>();
		if (Math.multiplyExact(count, value) <= remaining) {
			rule.penalized().forEach(member -> amounts.put(member.penaltyId(), value));
			return amounts;
		}
		long split = splitShare(remaining, count);
		rule.penalized().forEach(member -> amounts.put(member.penaltyId(), split));
		deductExcess(Math.multiplyExact(split, count) - remaining, expense.payerId(), rule, amounts);
		return amounts;
	}

	// 1인 계산값. 비율은 잔액이 아니라 원래 지출 금액에 곱해 100원 단위로 올린다
	private static long perMemberValue(PenaltyRuleInput rule, long expenseAmount, long remaining) {
		return switch (rule.moneyType()) {
			case FIXED_AMOUNT -> rule.amount();
			case RATIO -> Math.multiplyExact(
					Math.addExact(Math.multiplyExact(expenseAmount, rule.ratioPct()), RATIO_ROUND_UP) / RATIO_DIVISOR,
					ROUND_UNIT);
			case FULL -> remaining;
		};
	}

	// ceil(잔액 ÷ 인원)을 100원 단위로 올린다
	private static long splitShare(long remaining, long count) {
		long divisor = Math.multiplyExact(ROUND_UNIT, count);
		return Math.multiplyExact(Math.addExact(remaining, divisor - 1) / divisor, ROUND_UNIT);
	}

	// 올림 초과분은 벌칙자인 결제자 몫에서 먼저 빼고, 남으면 벌칙 id 내림차순으로 0원까지 뺀다
	private static void deductExcess(long excess, long payerId, PenaltyRuleInput rule, Map<Long, Long> amounts) {
		long left = excess;
		for (PenalizedMember member : rule.penalized()) {
			if (member.memberId() == payerId) {
				left = deduct(member.penaltyId(), left, amounts);
			}
		}
		for (PenalizedMember member : penaltyIdOrder(rule).reversed()) {
			left = deduct(member.penaltyId(), left, amounts);
		}
	}

	private static long deduct(long penaltyId, long excess, Map<Long, Long> amounts) {
		long amount = amounts.get(penaltyId);
		long deducted = Math.min(amount, excess);
		amounts.put(penaltyId, amount - deducted);
		return excess - deducted;
	}

	private static List<PenalizedMember> penaltyIdOrder(PenaltyRuleInput rule) {
		return rule.penalized().stream().sorted(Comparator.comparingLong(PenalizedMember::penaltyId)).toList();
	}
}
