package com.poco7250.moyeo.meeting.dto;

/**
 * 차수 체크인 결과(PRD M-7).
 */
public record CheckInResponse(Long roundId, Long memberId, boolean checkedIn) {
}
