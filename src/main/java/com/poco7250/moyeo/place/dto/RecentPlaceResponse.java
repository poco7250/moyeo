package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceLinkFactory;
import java.time.LocalDateTime;

/**
 * 지난 모임 장소 응답(같은 장소는 하나로 합침). lastUsedAt = 가장 최근 모임 시작 시각, useCount = 확정된 차수 수.
 */
public record RecentPlaceResponse(String label, String manualAddress, String manualMapUrl, String mapUrl,
		LocalDateTime lastUsedAt, int useCount) {

	/**
	 * 장소 값과 집계로 응답 DTO를 만든다.
	 */
	public static RecentPlaceResponse of(Place place, LocalDateTime lastUsedAt, int useCount) {
		return new RecentPlaceResponse(place.getLabel(), place.getManualAddress(), place.getManualMapUrl(),
				PlaceLinkFactory.mapUrl(place), lastUsedAt, useCount);
	}
}
