package com.poco7250.moyeo.settlement.calc;

import java.util.List;

/**
 * 벌칙의 올림 전 원래 계산값 합이 대상 지출 금액을 넘는지 판단한다(PRD 4.5 경고 규칙).
 * 소수점을 피하려고 모든 값을 100배 해서 비교한다.
 */
public final class PenaltyWarningEvaluator {

	private static final long SCALE = 100L;

	private PenaltyWarningEvaluator() {
	}

	/**
	 * 지출 1건을 대상으로 한 규칙들이 경고 대상이면 true.
	 * 같은 규칙의 벌칙자 수 때문에만 넘는 부분은 지출 금액에서 잘라 경고로 보지 않는다.
	 */
	public static boolean exceedsTarget(long expenseAmount, List<PenaltyRuleInput> rules) {
		long target = Math.multiplyExact(expenseAmount, SCALE);
		long total = 0L;
		for (PenaltyRuleInput rule : rules) {
			long value = scaledRawValue(rule, expenseAmount);
			long all = Math.multiplyExact(value, (long) rule.penalized().size());
			total = Math.addExact(total, Math.min(all, Math.max(value, target)));
		}
		return total > target;
	}

	// 1인의 올림 전 계산값 × 100
	private static long scaledRawValue(PenaltyRuleInput rule, long expenseAmount) {
		return switch (rule.moneyType()) {
			case FIXED_AMOUNT -> Math.multiplyExact(rule.amount(), SCALE);
			case RATIO -> Math.multiplyExact(expenseAmount, (long) rule.ratioPct());
			case FULL -> Math.multiplyExact(expenseAmount, SCALE);
		};
	}
}
