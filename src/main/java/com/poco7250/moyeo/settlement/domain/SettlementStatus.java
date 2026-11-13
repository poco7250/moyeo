package com.poco7250.moyeo.settlement.domain;

/**
 * 정산 상태. 모임 생성 시 DRAFT, 확정하면 CONFIRMED, 확정 후 금액 영향 변경이 생기면 REOPENED(재확정 필요).
 * REOPENED에서 다시 확정하면 리비전을 하나 올려 CONFIRMED로 돌아간다.
 */
public enum SettlementStatus {
	DRAFT,
	CONFIRMED,
	REOPENED
}
