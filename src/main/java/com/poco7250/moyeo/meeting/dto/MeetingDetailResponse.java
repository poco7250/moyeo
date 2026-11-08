package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingShareText;
import com.poco7250.moyeo.meeting.domain.Round;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 상세 응답. url은 단톡방에 공유할 링크(OG 미리보기 경로라 앱 내부 이동에는 id를 쓴다),
 * shareText는 카카오톡 공유 문구, host는 요청자가 호스트인지 여부다.
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
		String shareText,
		List<RoundResponse> rounds) {

	/**
	 * 모임·차수 엔티티와 요청자, 공유 URL로 응답 DTO를 만든다. 차수는 seq 순이어야 한다.
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
				MeetingShareText.kakaoText(meeting.getTitle(), meeting.getStartAt(),
						MeetingShareText.firstPlaceLabel(rounds), url),
				rounds.stream().map(RoundResponse::from).toList());
	}
}
