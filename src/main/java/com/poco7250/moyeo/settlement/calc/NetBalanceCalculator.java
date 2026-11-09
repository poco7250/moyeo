package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 회원별 순잔액(결제액 − 부담액)을 계산한다.
 */
public final class NetBalanceCalculator {

	private NetBalanceCalculator() {
	}

	/**
	 * 지출과 회원별 부담액으로 memberId 오름차순 정산 결과를 만든다.
	 *
	 * @throws IllegalStateException 순잔액 합이 0이 아니면(계산 오류)
	 */
	public static List<MemberSettlement> calculate(List<ExpenseInput> expenses, Map<Long, Long> shares) {
		Map<Long, Long> paid = new TreeMap<>();
		expenses.forEach(expense -> paid.merge(expense.payerId(), expense.amount(), Math::addExact));

		TreeSet<Long> memberIds = new TreeSet<>(paid.keySet());
		memberIds.addAll(shares.keySet());

		List<MemberSettlement> members = new ArrayList<>();
		long total = 0L;
		for (Long memberId : memberIds) {
			long share = shares.getOrDefault(memberId, 0L);
			long netBalance = Math.subtractExact(paid.getOrDefault(memberId, 0L), share);
			members.add(new MemberSettlement(memberId, share, netBalance));
			total = Math.addExact(total, netBalance);
		}
		if (total != 0L) {
			throw new IllegalStateException("순잔액 합이 0이 아닙니다: " + total);
		}
		return members;
	}
}
