package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 상세 응답. url은 단톡방에 공유할 모임 상세 링크, host는 요청자가 호스트인지 여부다.
 */
public record MeetingDetailResponse(
		Long id,
		Long groupId,
		String title,
		LocalDateTime startAt,
		String memo,
		LocalDateTime responseDeadline,
		Long hostMemberId,
		boolean host,
		String url,
		List<RoundResponse> rounds) {

	/**
	 * 모임·차수 엔티티와 요청자, 공유 URL로 응답 DTO를 만든다.
	 */
	public static MeetingDetailResponse of(Meeting meeting, List<Round> rounds, Long viewerId, String url) {
		return new MeetingDetailResponse(
				meeting.getId(),
				meeting.getGroup().getId(),
				meeting.getTitle(),
				meeting.getStartAt(),
				meeting.getMemo(),
				meeting.getResponseDeadline(),
				meeting.getHostMemberId(),
				meeting.isHost(viewerId),
				url,
				rounds.stream().map(RoundResponse::from).toList());
	}
}
