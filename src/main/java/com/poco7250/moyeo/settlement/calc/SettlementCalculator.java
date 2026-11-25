package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 정산 계산 진입점. 지출 분배 → 금액형 게임 벌칙 반영 → 순잔액 → 송금 안내 순서로 계산한다(PRD 4.5 정산 계산 순서).
 */
public final class SettlementCalculator {

	private SettlementCalculator() {
	}

	/**
	 * 모임의 지출·게임 벌칙으로 회원별 부담액·순잔액·송금 안내·경고·벌칙별 부담액을 계산한다.
	 */
	public static SettlementResult calculate(SettlementInput input) {
		Map<Long, Long> shares = new TreeMap<>();
		List<SettlementWarning> warnings = new ArrayList<>();
		for (ExpenseInput expense : input.expenses()) {
			ExpenseDistribution distribution = ShareDistributor.distribute(expense);
			distribution.shares().forEach((memberId, share) -> shares.merge(memberId, share, Math::addExact));
			warnings.addAll(distribution.warnings());
		}
		List<PenaltyChargeResult> charges = new ArrayList<>();
		input.gamePenalties().forEach(game -> charges.addAll(GamePenaltyAdjuster.apply(game, shares)));
		return toResult(input, shares, warnings, charges);
	}

	private static SettlementResult toResult(
			SettlementInput input,
			Map<Long, Long> shares,
			List<SettlementWarning> warnings,
			List<PenaltyChargeResult> charges) {
		List<MemberSettlement> members = NetBalanceCalculator.calculate(input.expenses(), shares);
		Map<Long, Long> netBalances = new TreeMap<>();
		members.forEach(member -> netBalances.put(member.memberId(), member.netBalance()));
		return new SettlementResult(members, TransferMinimizer.minimize(netBalances), warnings, charges);
	}
}
