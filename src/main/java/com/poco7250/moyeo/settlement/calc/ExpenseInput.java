package com.poco7250.moyeo.settlement.calc;

import java.util.List;
import java.util.Set;

/**
 * 정산 계산에 쓰는 지출 1건.
 *
 * @param attendeeIds 분배 대상이 되는 차수 참석자(ATTEND) 회원 id
 * @param adjustments 분배 조정. 참석자가 아닌 회원의 조정은 계산에서 무시한다
 */
public record ExpenseInput(
		long expenseId,
		long amount,
		long payerId,
		Set<Long> attendeeIds,
		List<ShareAdjustment> adjustments) {
}
