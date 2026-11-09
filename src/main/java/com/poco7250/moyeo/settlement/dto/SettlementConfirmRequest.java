package com.poco7250.moyeo.settlement.dto;

/**
 * 정산 확정 요청. 필드를 생략할 수 있어야 해서 Boolean으로 받는다.
 *
 * @param acknowledgeWarnings 계산 경고를 확인했으면 true
 */
public record SettlementConfirmRequest(Boolean acknowledgeWarnings) {

	/**
	 * 경고 확인 여부를 돌려준다. 값이 없으면 확인하지 않은 것으로 본다.
	 */
	public boolean acknowledged() {
		return Boolean.TRUE.equals(acknowledgeWarnings);
	}
}
