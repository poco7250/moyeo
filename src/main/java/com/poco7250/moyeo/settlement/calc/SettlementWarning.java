package com.poco7250.moyeo.settlement.calc;

/**
 * 지출 1건에서 발생한 계산 경고.
 */
public record SettlementWarning(SettlementWarningType type, long expenseId) {
}
