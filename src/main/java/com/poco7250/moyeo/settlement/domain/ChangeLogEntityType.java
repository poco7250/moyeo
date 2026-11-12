package com.poco7250.moyeo.settlement.domain;

/**
 * 변경 로그의 대상 엔티티 종류. 나머지 벌칙 값은 해당 Task에서 추가한다.
 */
public enum ChangeLogEntityType {
	EXPENSE,
	EXPENSE_SHARE,
	ATTENDANCE,
	GAME_SESSION,
	PENALTY_RULE
}
