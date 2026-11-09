package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.Expense;
import java.util.List;

/**
 * 변경 로그에 남기는 지출 값. 지출을 지워도 분배 조정까지 알 수 있게 조정을 함께 담는다.
 */
public record ExpenseSnapshot(
		Long roundId,
		Long payerId,
		long amount,
		String memo,
		List<ExpenseShareSnapshot> shares) implements ChangeLogSnapshot {

	/**
	 * 지출의 현재 값을 스냅샷으로 만든다.
	 */
	public static ExpenseSnapshot from(Expense expense) {
		return new ExpenseSnapshot(expense.getRound().getId(), expense.getPayer().getId(), expense.getAmount(),
				expense.getMemo(), ExpenseShareSnapshot.listOf(expense));
	}
}
