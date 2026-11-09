package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.Expense;
import java.util.Comparator;
import java.util.List;

/**
 * 변경 로그에 남기는 회원 한 명의 분배 조정 값.
 */
public record ExpenseShareSnapshot(Long memberId, Long amount, Integer weight, boolean excluded) {

	/**
	 * 지출의 분배 조정을 회원 id 순 스냅샷으로 변환한다.
	 */
	public static List<ExpenseShareSnapshot> listOf(Expense expense) {
		return expense.getShares().stream()
				.map(share -> new ExpenseShareSnapshot(share.getMember().getId(), share.getAmount(),
						share.getWeight(), share.isExcluded()))
				.sorted(Comparator.comparing(ExpenseShareSnapshot::memberId))
				.toList();
	}
}
