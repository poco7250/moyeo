package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.Expense;
import java.util.List;

/**
 * 지출 응답.
 *
 * @param receipt 영수증 사진 정보. 없으면 null
 */
public record ExpenseResponse(
		Long id,
		Long roundId,
		Long payerId,
		long amount,
		String memo,
		List<ExpenseShareResponse> shares,
		ExpenseReceiptResponse receipt) {

	/**
	 * 지출 엔티티와 영수증 정보(없으면 null)를 응답으로 변환한다.
	 */
	public static ExpenseResponse of(Expense expense, ExpenseReceiptResponse receipt) {
		return new ExpenseResponse(expense.getId(), expense.getRound().getId(), expense.getPayer().getId(),
				expense.getAmount(), expense.getMemo(), ExpenseShareResponse.listOf(expense), receipt);
	}
}
