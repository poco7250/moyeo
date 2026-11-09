package com.poco7250.moyeo.settlement.calc;

import java.util.List;

/**
 * 모임 하나의 정산 계산 입력.
 */
public record SettlementInput(List<ExpenseInput> expenses) {
}
