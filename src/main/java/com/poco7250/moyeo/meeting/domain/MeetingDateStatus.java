package com.poco7250.moyeo.meeting.domain;

/**
 * 모임 날짜 상태(PRD M-5). 날짜 미정 모임은 VOTING으로 만들고, 호스트가 후보를 확정하면 CONFIRMED가 된다.
 */
public enum MeetingDateStatus {
	/** 날짜 후보 투표 중(startAt 없음) */
	VOTING,
	/** 날짜 확정(startAt 있음) */
	CONFIRMED
}
