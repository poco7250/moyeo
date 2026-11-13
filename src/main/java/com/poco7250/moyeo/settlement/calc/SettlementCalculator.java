package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 정산 계산 진입점. 지출별 벌칙 차감 → 남은 금액 분배 → 순잔액 → 송금 안내 순서로 계산한다(PRD 4.5 정산 계산 순서).
 */
public final class SettlementCalculator {

	private SettlementCalculator() {
	}

	/**
	 * 모임의 지출·벌칙 조건으로 회원별 부담액·순잔액·송금 안내·경고·벌칙별 부담액을 계산한다.
	 *
	 * @throws IllegalArgumentException 벌칙 조건이 입력에 없는 지출을 가리키면
	 */
	public static SettlementResult calculate(SettlementInput input) {
		Map<Long, List<PenaltyRuleInput>> rulesByExpense = rulesByExpense(input);
		Map<Long, Long> shares = new TreeMap<>();
		List<SettlementWarning> warnings = new ArrayList<>();
		List<PenaltyChargeResult> charges = new ArrayList<>();
		for (ExpenseInput expense : input.expenses()) {
			List<PenaltyRuleInput> rules = rulesByExpense.getOrDefault(expense.expenseId(), List.of());
			PenaltyDeduction deduction = PenaltyChargeCalculator.calculate(expense, rules);
			deduction.charges().forEach(charge -> shares.merge(charge.memberId(), charge.amount(), Math::addExact));
			charges.addAll(deduction.charges());

			ExpenseDistribution distribution = ShareDistributor.distribute(remainderOf(expense, deduction));
			distribution.shares().forEach((memberId, share) -> shares.merge(memberId, share, Math::addExact));
			warnings.addAll(distribution.warnings());
			if (PenaltyWarningEvaluator.exceedsTarget(expense.amount(), rules)) {
				warnings.add(new SettlementWarning(SettlementWarningType.PENALTY_EXCEEDS_TARGET, expense.expenseId()));
			}
		}
		return toResult(input, shares, warnings, charges);
	}

	private static Map<Long, List<PenaltyRuleInput>> rulesByExpense(SettlementInput input) {
		Set<Long> expenseIds = input.expenses().stream().map(ExpenseInput::expenseId).collect(Collectors.toSet());
		for (PenaltyRuleInput rule : input.penaltyRules()) {
			if (!expenseIds.contains(rule.targetExpenseId())) {
				throw new IllegalArgumentException("벌칙 대상 지출이 입력에 없습니다: " + rule.targetExpenseId());
			}
		}
		return input.penaltyRules().stream().collect(Collectors.groupingBy(PenaltyRuleInput::targetExpenseId));
	}

	// 벌칙을 떼고 남은 금액을 벌칙자를 뺀 참석자가 나누도록 지출 입력을 바꾼다. 부담액이 0원인 벌칙자도 뺀다
	private static ExpenseInput remainderOf(ExpenseInput expense, PenaltyDeduction deduction) {
		if (deduction.charges().isEmpty()) {
			return expense;
		}
		Set<Long> attendeeIds = new HashSet<>(expense.attendeeIds());
		deduction.charges().forEach(charge -> attendeeIds.remove(charge.memberId()));
		return new ExpenseInput(expense.expenseId(), deduction.remaining(), expense.payerId(), attendeeIds,
				expense.adjustments());
	}

	// 순잔액은 벌칙을 떼기 전 원래 지출 금액으로 계산한다
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
