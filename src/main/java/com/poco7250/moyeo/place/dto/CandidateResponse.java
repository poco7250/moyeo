package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceLinkFactory;

/**
 * 장소 후보 응답. mapUrl은 저장하지 않고 조회 시점에 만든 지도 링크다.
 */
public record CandidateResponse(Long id, String label, String manualAddress, String manualMapUrl, String mapUrl,
		Long proposedByMemberId) {

	/**
	 * 엔티티로 응답 DTO를 만든다. 제안자는 LAZY 프록시의 id만 읽어 조회 쿼리를 내지 않는다.
	 */
	public static CandidateResponse from(PlaceCandidate candidate) {
		Place place = candidate.getPlace();
		return new CandidateResponse(candidate.getId(), place.getLabel(), place.getManualAddress(),
				place.getManualMapUrl(), PlaceLinkFactory.mapUrl(place), candidate.getProposedBy().getId());
	}
}
