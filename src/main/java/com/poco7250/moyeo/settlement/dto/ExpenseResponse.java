package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.Expense;
import java.util.List;

/**
 * 지출 응답.
 */
public record ExpenseResponse(
		Long id,
		Long roundId,
		Long payerId,
		long amount,
		String memo,
		List<ExpenseShareResponse> shares) {

	/**
	 * 지출 엔티티를 응답으로 변환한다.
	 */
	public static ExpenseResponse from(Expense expense) {
		return new ExpenseResponse(expense.getId(), expense.getRound().getId(), expense.getPayer().getId(),
				expense.getAmount(), expense.getMemo(), ExpenseShareResponse.listOf(expense));
	}
}
