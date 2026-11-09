package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.calc.SettlementWarning;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;

/**
 * 정산 계산 경고 응답.
 */
public record SettlementWarningResponse(SettlementWarningType type, Long expenseId) {

	/**
	 * 계산 경고를 응답으로 변환한다.
	 */
	public static SettlementWarningResponse from(SettlementWarning warning) {
		return new SettlementWarningResponse(warning.type(), warning.expenseId());
	}
}
