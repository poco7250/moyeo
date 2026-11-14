package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;

/**
 * 날짜 확정 알림(MEETING_DATE_CONFIRMED) 본문.
 */
public record MeetingDateConfirmedResponse(Long meetingId, Long candidateId, LocalDateTime startAt) {
}
