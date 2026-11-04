package com.poco7250.moyeo.place.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceLinkFactoryTest {

	@Test
	@DisplayName("사용자가 넣은 지도 링크가 있으면 그대로 쓴다")
	void manualMapUrlFirst() {
		Place place = Place.of("볼링장", "서울 강남구", "https://place.map.kakao.com/123");

		assertThat(PlaceLinkFactory.mapUrl(place)).isEqualTo("https://place.map.kakao.com/123");
	}

	@Test
	@DisplayName("지도 링크가 없으면 주소로 카카오맵 검색 링크를 만든다")
	void addressKeyword() {
		Place place = Place.of("볼링장", "강남대로 1", null);

		assertThat(PlaceLinkFactory.mapUrl(place))
				.isEqualTo("https://map.kakao.com/link/search/%EA%B0%95%EB%82%A8%EB%8C%80%EB%A1%9C%201");
	}

	@Test
	@DisplayName("주소도 없거나 공백이면 이름으로 검색 링크를 만든다")
	void labelKeyword() {
		assertThat(PlaceLinkFactory.mapUrl(Place.of("강남 볼링", null, null)))
				.isEqualTo("https://map.kakao.com/link/search/%EA%B0%95%EB%82%A8%20%EB%B3%BC%EB%A7%81");
		assertThat(PlaceLinkFactory.mapUrl(Place.of("강남 볼링", "  ", " ")))
				.isEqualTo("https://map.kakao.com/link/search/%EA%B0%95%EB%82%A8%20%EB%B3%BC%EB%A7%81");
	}

	@Test
	@DisplayName("경로를 깨는 문자(/·?·#·+)도 인코딩한다")
	void reservedCharacters() {
		assertThat(PlaceLinkFactory.mapUrl(Place.of("A/B?C#D+E", null, null)))
				.isEqualTo("https://map.kakao.com/link/search/A%2FB%3FC%23D%2BE");
	}
}
