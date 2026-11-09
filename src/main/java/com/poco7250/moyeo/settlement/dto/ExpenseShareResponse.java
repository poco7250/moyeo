package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.ExpenseShare;
import java.util.Comparator;
import java.util.List;

/**
 * 회원 한 명의 분배 조정 응답.
 */
public record ExpenseShareResponse(Long memberId, Long amount, Integer weight, boolean excluded) {

	/**
	 * 지출의 분배 조정을 회원 id 순으로 변환한다.
	 */
	public static List<ExpenseShareResponse> listOf(Expense expense) {
		return expense.getShares().stream()
				.map(ExpenseShareResponse::from)
				.sorted(Comparator.comparing(ExpenseShareResponse::memberId))
				.toList();
	}

	private static ExpenseShareResponse from(ExpenseShare share) {
		return new ExpenseShareResponse(share.getMember().getId(), share.getAmount(), share.getWeight(),
				share.isExcluded());
	}
}
