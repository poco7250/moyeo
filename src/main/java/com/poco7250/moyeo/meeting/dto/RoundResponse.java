package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import java.time.LocalDateTime;

/**
 * 차수 응답. 장소 상태로 모임 상세의 장소 탭 표시를 정한다.
 */
public record RoundResponse(Long id, int seq, String name, PlaceStatus placeStatus, LocalDateTime placeVoteDeadline) {

	/**
	 * 엔티티로 응답 DTO를 만든다.
	 */
	public static RoundResponse from(Round round) {
		return new RoundResponse(round.getId(), round.getSeq(), round.getName(),
				round.getPlaceStatus(), round.getPlaceVoteDeadline());
	}
}
