package com.poco7250.moyeo.place.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카카오 로컬 키워드 검색 응답. 필요한 필드만 매핑하며, 이 값은 클라이언트에 전달만 하고 저장하지 않는다.
 *
 * @param documents 검색 결과 장소 목록
 * @param meta      페이지 정보
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalSearchResponse(List<Document> documents, Meta meta) {

	/**
	 * 검색 결과 장소 하나.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Document(
			String id,
			@JsonProperty("place_name") String placeName,
			@JsonProperty("category_name") String categoryName,
			@JsonProperty("address_name") String addressName,
			@JsonProperty("road_address_name") String roadAddressName,
			String phone,
			@JsonProperty("place_url") String placeUrl) {
	}

	/**
	 * 페이지 정보.
	 *
	 * @param isEnd 마지막 페이지 여부
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Meta(@JsonProperty("is_end") boolean isEnd) {
	}
}
