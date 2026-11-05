package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 차수의 장소 결정 상태(상태·투표 마감·확정 후보·후보 목록). 득표 현황은 RoundVotesResponse로 따로 조회한다.
 */
public record RoundPlaceResponse(Long roundId, PlaceStatus placeStatus, LocalDateTime placeVoteDeadline,
		Long confirmedCandidateId, List<CandidateResponse> candidates) {

	/**
	 * 차수와 그 차수의 후보 목록으로 응답 DTO를 만든다.
	 */
	public static RoundPlaceResponse of(Round round, List<PlaceCandidate> candidates) {
		PlaceCandidate confirmed = round.getConfirmedCandidate();
		return new RoundPlaceResponse(round.getId(), round.getPlaceStatus(), round.getPlaceVoteDeadline(),
				confirmed == null ? null : confirmed.getId(),
				candidates.stream().map(CandidateResponse::from).toList());
	}
}
