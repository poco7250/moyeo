package com.poco7250.moyeo.place.domain;

import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

/**
 * 장소 지도 링크를 만든다. 카카오 API를 호출하거나 저장하지 않고 사용자 입력 문자열만 쓴다.
 * 장소 ID가 없으므로 /link/map·/link/to 링크는 만들지 않는다.
 */
public final class PlaceLinkFactory {

	static final String KAKAO_MAP_SEARCH_URL = "https://map.kakao.com/link/search/";

	private PlaceLinkFactory() {
	}

	/**
	 * 사용자가 넣은 지도 링크가 있으면 그것, 없으면 주소(없으면 이름)로 카카오맵 검색 링크를 돌려준다.
	 */
	public static String mapUrl(Place place) {
		if (StringUtils.hasText(place.getManualMapUrl())) {
			return place.getManualMapUrl();
		}
		String keyword = StringUtils.hasText(place.getManualAddress()) ? place.getManualAddress() : place.getLabel();
		// URLEncoder는 공백을 '+'로 바꿔 경로에서 깨지므로 경로 세그먼트 인코딩(공백 → %20)을 쓴다.
		// '+'는 경로에서 허용 문자라 그대로 남는데, 서버가 공백으로 읽을 수 있어 %2B로 바꾼다
		String encoded = UriUtils.encodePathSegment(keyword.strip(), StandardCharsets.UTF_8);
		return KAKAO_MAP_SEARCH_URL + encoded.replace("+", "%2B");
	}
}
