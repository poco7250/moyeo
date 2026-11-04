package com.poco7250.moyeo.meeting.domain;

/**
 * 차수별 장소 결정 상태. 전이 규칙은 Round의 confirmDirectly·openVoting·confirm에 있다.
 */
public enum PlaceStatus {
	UNDECIDED,
	VOTING,
	CONFIRMED
}
