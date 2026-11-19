package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.place.domain.FavoritePlace;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceLinkFactory;
import java.time.LocalDateTime;

/**
 * 그룹 단골 장소 응답. mapUrl은 저장하지 않고 조회 시점에 만든 지도 링크다.
 */
public record FavoritePlaceResponse(Long id, String label, String manualAddress, String manualMapUrl, String mapUrl,
		Long createdByMemberId, LocalDateTime createdAt) {

	/**
	 * 엔티티로 응답 DTO를 만든다. 저장한 회원은 LAZY 프록시의 id만 읽어 조회 쿼리를 내지 않는다.
	 */
	public static FavoritePlaceResponse from(FavoritePlace favorite) {
		Place place = favorite.getPlace();
		return new FavoritePlaceResponse(favorite.getId(), place.getLabel(), place.getManualAddress(),
				place.getManualMapUrl(), PlaceLinkFactory.mapUrl(place), favorite.getCreatedBy().getId(),
				favorite.getCreatedAt());
	}
}
