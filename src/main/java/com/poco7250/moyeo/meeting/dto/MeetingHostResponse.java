package com.poco7250.moyeo.meeting.dto;

/**
 * 모임 호스트 위임 결과. 실시간 MEETING_HOST_CHANGED payload로도 쓴다.
 */
public record MeetingHostResponse(Long meetingId, Long hostMemberId) {
}
