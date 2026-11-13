package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.calc.NetBalanceDelta;

/**
 * 직전 확정 리비전 대비 회원별 순잔액 변동 응답. 순잔액은 받을 돈이 +, 낼 돈이 −다.
 */
public record NetBalanceChangeResponse(Long memberId, long previousNetBalance, long currentNetBalance, long delta) {

	/**
	 * 계산한 변동액을 응답으로 변환한다.
	 */
	public static NetBalanceChangeResponse from(NetBalanceDelta delta) {
		return new NetBalanceChangeResponse(delta.memberId(), delta.previousNetBalance(), delta.currentNetBalance(),
				delta.delta());
	}
}
