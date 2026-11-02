package com.poco7250.moyeo.meeting.dto;

import java.util.List;

/**
 * 한 멤버의 차수별 응답(응답 저장 결과, 실시간 ATTENDANCE_CHANGED 본문). 차수 순서대로 담는다.
 */
public record MemberAttendanceResponse(Long memberId, List<RoundAttendanceItem> rounds) {
}
