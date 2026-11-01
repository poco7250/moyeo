package com.poco7250.moyeo.settlement.domain;

/**
 * 정산 상태. 모임 생성 시 DRAFT, 확정하면 CONFIRMED, 확정 후 금액 영향 변경이 생기면 REOPENED(재확정 필요).
 * 전이는 Task 021·024에서 구현한다.
 */
public enum SettlementStatus {
	DRAFT,
	CONFIRMED,
	REOPENED
}
