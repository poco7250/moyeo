package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.meeting.domain.AttendanceStatus;

/**
 * 변경 로그에 남기는 참석 응답 값.
 */
public record AttendanceSnapshot(Long roundId, Long memberId, AttendanceStatus status) implements ChangeLogSnapshot {
}
