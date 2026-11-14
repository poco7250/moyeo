package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingDateStatus;
import java.time.LocalDateTime;

/**
 * 그룹 홈 모임 목록의 한 줄 요약. 날짜 투표 중이면 startAt은 null이다.
 */
public record MeetingSummaryResponse(Long id, String title, LocalDateTime startAt, MeetingDateStatus dateStatus,
		Long hostMemberId) {

	/**
	 * 엔티티로 응답 DTO를 만든다.
	 */
	public static MeetingSummaryResponse from(Meeting meeting) {
		return new MeetingSummaryResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(),
				meeting.getDateStatus(), meeting.getHostMemberId());
	}
}
