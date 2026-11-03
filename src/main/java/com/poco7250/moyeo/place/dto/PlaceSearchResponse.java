package com.poco7250.moyeo.place.dto;

import java.util.List;

/**
 * 장소 검색 응답. 카카오 결과를 저장·캐시 없이 그대로 옮긴다.
 *
 * @param places 검색 결과 목록
 * @param page   현재 페이지(1부터)
 * @param isEnd  마지막 페이지 여부
 */
public record PlaceSearchResponse(List<PlaceSearchItem> places, int page, boolean isEnd) {

	/**
	 * 카카오 검색 응답을 클라이언트 응답으로 바꾼다. 목록·메타가 비어 있으면 빈 마지막 페이지로 본다.
	 */
	public static PlaceSearchResponse of(KakaoLocalSearchResponse response, int page) {
		List<PlaceSearchItem> places = response.documents() == null ? List.of()
				: response.documents().stream().map(PlaceSearchItem::from).toList();
		boolean isEnd = response.meta() == null || response.meta().isEnd();
		return new PlaceSearchResponse(places, page, isEnd);
	}
}
