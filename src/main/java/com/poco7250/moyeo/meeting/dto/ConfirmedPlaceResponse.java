package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceLinkFactory;

/**
 * 차수의 확정 장소 응답. mapUrl은 사용자가 넣은 지도 링크, 없으면 카카오맵 검색 링크다.
 */
public record ConfirmedPlaceResponse(String label, String mapUrl) {

	/**
	 * 장소 값으로 응답 DTO를 만든다. 확정 전(null)이면 null을 돌려준다.
	 */
	public static ConfirmedPlaceResponse from(Place place) {
		return place == null ? null : new ConfirmedPlaceResponse(place.getLabel(), PlaceLinkFactory.mapUrl(place));
	}
}
