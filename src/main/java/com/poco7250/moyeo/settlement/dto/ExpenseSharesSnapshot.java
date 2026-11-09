package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.Expense;
import java.util.List;

/**
 * 변경 로그에 남기는 지출의 분배 조정 전체 값. 조정은 지출 단위로 한 번에 교체된다.
 */
public record ExpenseSharesSnapshot(Long expenseId, List<ExpenseShareSnapshot> shares) implements ChangeLogSnapshot {

	/**
	 * 지출의 현재 분배 조정을 스냅샷으로 만든다.
	 */
	public static ExpenseSharesSnapshot from(Expense expense) {
		return new ExpenseSharesSnapshot(expense.getId(), ExpenseShareSnapshot.listOf(expense));
	}
}
