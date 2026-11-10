package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.meeting.domain.AttendanceStatus;

/**
 * 참석 응답 한 건의 실제 변경 내용.
 *
 * @param attendanceId 참석 행 id
 * @param roundId 차수 id
 * @param memberId 응답 대상 회원 id
 * @param before 변경 전 상태. 첫 응답이면 null
 * @param after 변경 후 상태
 */
public record AttendanceChange(Long attendanceId, Long roundId, Long memberId, AttendanceStatus before,
		AttendanceStatus after) {
}
