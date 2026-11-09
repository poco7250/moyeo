package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 지출 1건을 회원별 부담액으로 나눈다(PRD S-2, S-3, S-8).
 * 고정 금액을 먼저 떼고, 남은 금액을 가중치대로 100원 단위 올림해 나눈 뒤 초과분을 차감한다.
 */
public final class ShareDistributor {

	private static final int DEFAULT_WEIGHT = 1;
	private static final long ROUND_UNIT = 100L;

	private ShareDistributor() {
	}

	/**
	 * 지출 1건의 회원별 부담액과 경고를 계산한다. 부담액 합은 항상 지출 금액과 같다.
	 */
	public static ExpenseDistribution distribute(ExpenseInput expense) {
		NavigableMap<Long, ShareAdjustment> adjustments = attendeeAdjustments(expense);
		Map<Long, Long> shares = new TreeMap<>();
		expense.attendeeIds().forEach(memberId -> shares.put(memberId, 0L));
		List<SettlementWarning> warnings = new ArrayList<>();

		long remaining = chargeFixedAmounts(expense, adjustments, shares, warnings);
		NavigableMap<Long, Integer> pool = weightPool(expense, adjustments);
		if (!pool.isEmpty()) {
			distributePool(remaining, expense.payerId(), pool, shares);
		} else if (remaining > 0) {
			shares.merge(expense.payerId(), remaining, Long::sum);
			warnings.add(new SettlementWarning(SettlementWarningType.NO_SHARE_TARGET, expense.expenseId()));
		}
		return new ExpenseDistribution(shares, warnings);
	}

	// 참석자가 아닌 회원의 조정은 버린다. 저장된 조정은 참석으로 바뀌면 다시 적용된다
	private static NavigableMap<Long, ShareAdjustment> attendeeAdjustments(ExpenseInput expense) {
		NavigableMap<Long, ShareAdjustment> adjustments = new TreeMap<>();
		for (ShareAdjustment adjustment : expense.adjustments()) {
			if (expense.attendeeIds().contains(adjustment.memberId())) {
				adjustments.put(adjustment.memberId(), adjustment);
			}
		}
		return adjustments;
	}

	// memberId 오름차순으로 고정 금액을 부담시키고 남은 금액을 돌려준다
	private static long chargeFixedAmounts(
			ExpenseInput expense,
			Map<Long, ShareAdjustment> adjustments,
			Map<Long, Long> shares,
			List<SettlementWarning> warnings) {
		long remaining = expense.amount();
		boolean overflowed = false;
		for (ShareAdjustment adjustment : adjustments.values()) {
			if (!isFixed(adjustment)) {
				continue;
			}
			long charged = Math.min(adjustment.fixedAmount(), remaining);
			shares.put(adjustment.memberId(), charged);
			remaining -= charged;
			overflowed |= charged < adjustment.fixedAmount();
		}
		if (overflowed) {
			warnings.add(new SettlementWarning(SettlementWarningType.FIXED_AMOUNT_OVERFLOW, expense.expenseId()));
		}
		return remaining;
	}

	// 제외·고정 금액이 아닌 참석자의 가중치 목록을 만든다
	private static NavigableMap<Long, Integer> weightPool(ExpenseInput expense, Map<Long, ShareAdjustment> adjustments) {
		NavigableMap<Long, Integer> pool = new TreeMap<>();
		for (Long memberId : expense.attendeeIds()) {
			ShareAdjustment adjustment = adjustments.get(memberId);
			if (adjustment == null) {
				pool.put(memberId, DEFAULT_WEIGHT);
			} else if (!adjustment.excluded() && !isFixed(adjustment)) {
				pool.put(memberId, adjustment.weight() == null ? DEFAULT_WEIGHT : adjustment.weight());
			}
		}
		return pool;
	}

	private static boolean isFixed(ShareAdjustment adjustment) {
		return !adjustment.excluded() && adjustment.fixedAmount() != null;
	}

	private static void distributePool(
			long amount,
			long payerId,
			NavigableMap<Long, Integer> pool,
			Map<Long, Long> shares) {
		long totalWeight = pool.values().stream().mapToLong(Integer::longValue).sum();
		long collected = 0L;
		for (Map.Entry<Long, Integer> entry : pool.entrySet()) {
			long share = roundedShare(amount, entry.getValue(), totalWeight);
			shares.put(entry.getKey(), share);
			collected = Math.addExact(collected, share);
		}
		deductExcess(collected - amount, payerId, pool, shares);
	}

	// ceil(금액 × 가중치 ÷ 가중치 합)을 100원 단위로 올린다
	private static long roundedShare(long amount, long weight, long totalWeight) {
		long divisor = Math.multiplyExact(ROUND_UNIT, totalWeight);
		long dividend = Math.addExact(Math.multiplyExact(amount, weight), divisor - 1);
		return Math.multiplyExact(dividend / divisor, ROUND_UNIT);
	}

	// 초과분은 결제자 몫에서 먼저 빼고, 남으면 memberId 내림차순으로 0원까지 뺀다
	private static void deductExcess(
			long excess,
			long payerId,
			NavigableMap<Long, Integer> pool,
			Map<Long, Long> shares) {
		long left = pool.containsKey(payerId) ? deduct(payerId, excess, shares) : excess;
		for (Long memberId : pool.descendingKeySet()) {
			left = deduct(memberId, left, shares);
		}
	}

	private static long deduct(long memberId, long excess, Map<Long, Long> shares) {
		long share = shares.get(memberId);
		long deducted = Math.min(share, excess);
		shares.put(memberId, share - deducted);
		return excess - deducted;
	}
}
