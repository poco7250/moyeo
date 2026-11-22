package com.poco7250.moyeo.meeting.dto;

import java.util.List;

/**
 * 모임의 현재 게스트 목록. 실시간 MEETING_GUESTS_CHANGED payload로 쓴다.
 *
 * @param guests 추가 순
 */
public record MeetingGuestsResponse(Long meetingId, List<MeetingGuestResponse> guests) {
}
