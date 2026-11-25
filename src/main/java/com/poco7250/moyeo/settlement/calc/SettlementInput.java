package com.poco7250.moyeo.settlement.calc;

import java.util.List;

/**
 * 모임 하나의 정산 계산 입력.
 *
 * @param gamePenalties 정산에 반영할 금액형 게임 벌칙(게임 순번 순)
 */
public record SettlementInput(List<ExpenseInput> expenses, List<GamePenaltyInput> gamePenalties) {

	/**
	 * 벌칙 없이 지출만으로 입력을 만든다.
	 */
	public SettlementInput(List<ExpenseInput> expenses) {
		this(expenses, List.of());
	}
}
