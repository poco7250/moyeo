package com.poco7250.moyeo.settlement.calc;

/**
 * 계산은 끝났지만 사용자가 확인해야 하는 상황의 종류.
 */
public enum SettlementWarningType {
	// 고정 금액 합이 지출 금액을 넘어 일부만 부담시켰다
	FIXED_AMOUNT_OVERFLOW,
	// 남은 금액을 나눌 사람이 없어 결제자가 부담했다
	NO_SHARE_TARGET
}
