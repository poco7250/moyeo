package com.poco7250.moyeo.settlement.domain;

/**
 * 변경 로그의 대상 엔티티 종류.
 */
public enum ChangeLogEntityType {
	EXPENSE,
	EXPENSE_SHARE,
	ATTENDANCE,
	GAME_SESSION,
	PENALTY_RULE,

	/** 게임 단위 벌칙 면제·면제 취소. 전원이 함께 바뀌므로 대상 id는 게임 id다 */
	PENALTY
}
