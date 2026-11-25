package com.poco7250.moyeo.penalty.domain;

/**
 * 벌칙 상태.
 */
public enum PenaltyStatus {

	/** 벌칙자 확정, 정산 금액 없음(행동형·벌칙 없음 게임은 여기에 머문다) */
	ASSIGNED,

	/** 금액형 게임 벌칙, 정산에 반영 */
	APPLIED,

	/** 호스트·총무가 금액 벌칙을 면제 */
	WAIVED,

	/** 다시 돌리기로 무효. 다른 상태로 돌아가지 않는다 */
	VOIDED
}
