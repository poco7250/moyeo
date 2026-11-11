package com.poco7250.moyeo.penalty.domain;

/**
 * 벌칙 금액 조건의 종류.
 */
public enum PenaltyMoneyType {

	/** 고정 금액 */
	FIXED_AMOUNT,

	/** 대상 지출의 비율(%) */
	RATIO,

	/** 대상 지출 전액 */
	FULL
}
