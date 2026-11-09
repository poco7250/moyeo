package com.poco7250.moyeo.settlement.calc;

import java.util.List;
import java.util.Map;

/**
 * 지출 1건의 분배 결과.
 *
 * @param shares 회원 id별 부담액. 부담액 합은 지출 금액과 같다
 */
public record ExpenseDistribution(Map<Long, Long> shares, List<SettlementWarning> warnings) {
}
