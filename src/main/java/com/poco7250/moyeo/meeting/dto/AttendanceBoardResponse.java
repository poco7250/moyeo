package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 참석 현황판(PRD M-4). closed는 지금 기준 응답 마감 여부다.
 */
public record AttendanceBoardResponse(LocalDateTime responseDeadline, boolean closed, List<RoundBoard> rounds) {
}
