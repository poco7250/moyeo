package com.poco7250.moyeo.meeting.domain;

/**
 * 차수별 참석 응답. 응답하지 않은 멤버는 Attendance 행이 없다(현황판의 "미응답").
 */
public enum AttendanceStatus {
	ATTEND,
	ABSENT,
	UNDECIDED
}
