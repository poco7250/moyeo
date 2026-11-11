package com.poco7250.moyeo.penalty.domain;

/**
 * 벌칙 상태.
 */
public enum PenaltyStatus {

	/** 벌칙자 확정, 금액 조건 미설정(행동형은 여기에 머문다) */
	ASSIGNED,

	/** 금액 조건 설정 완료, 정산에 반영 */
	APPLIED,

	/** 금액 벌칙 없음으로 확정 */
	WAIVED,

	/** 다시 돌리기로 무효. 다른 상태로 돌아가지 않는다 */
	VOIDED
}
