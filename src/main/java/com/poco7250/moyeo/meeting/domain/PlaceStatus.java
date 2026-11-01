package com.poco7250.moyeo.meeting.domain;

/**
 * 차수별 장소 결정 상태. UNDECIDED → VOTING → CONFIRMED 순으로 진행한다(전이는 Task 014·016).
 */
public enum PlaceStatus {
	UNDECIDED,
	VOTING,
	CONFIRMED
}
