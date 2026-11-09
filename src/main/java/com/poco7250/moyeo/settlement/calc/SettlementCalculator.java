package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 정산 계산 진입점. 지출별 분배 → 순잔액 → 송금 안내 순서로 계산한다(벌칙은 Task 035에서 추가).
 */
public final class SettlementCalculator {

	private SettlementCalculator() {
	}

	/**
	 * 모임의 지출 목록으로 회원별 부담액·순잔액·송금 안내·경고를 계산한다.
	 */
	public static SettlementResult calculate(SettlementInput input) {
		Map<Long, Long> shares = new TreeMap<>();
		List<SettlementWarning> warnings = new ArrayList<>();
		for (ExpenseInput expense : input.expenses()) {
			ExpenseDistribution distribution = ShareDistributor.distribute(expense);
			distribution.shares().forEach((memberId, share) -> shares.merge(memberId, share, Math::addExact));
			warnings.addAll(distribution.warnings());
		}

		List<MemberSettlement> members = NetBalanceCalculator.calculate(input.expenses(), shares);
		Map<Long, Long> netBalances = new TreeMap<>();
		members.forEach(member -> netBalances.put(member.memberId(), member.netBalance()));
		return new SettlementResult(members, TransferMinimizer.minimize(netBalances), warnings);
	}
}
