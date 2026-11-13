package com.poco7250.moyeo.settlement.calc;

import java.util.List;

/**
 * 모임 하나의 정산 계산 입력.
 *
 * @param penaltyRules 정산에 반영할 벌칙 금액 조건. 대상 지출은 expenses 안에 있어야 한다
 */
public record SettlementInput(List<ExpenseInput> expenses, List<PenaltyRuleInput> penaltyRules) {

	/**
	 * 벌칙 없이 지출만으로 입력을 만든다.
	 */
	public SettlementInput(List<ExpenseInput> expenses) {
		this(expenses, List.of());
	}
}
