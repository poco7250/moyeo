package com.poco7250.moyeo.meeting.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.place.domain.Place;
import org.junit.jupiter.api.Test;

class ConfirmedPlaceResponseTest {

	private static final String SEARCH_URL = "https://map.kakao.com/link/search/";

	@Test
	void 사용자_지도_링크가_있으면_그_링크를_쓴다() {
		ConfirmedPlaceResponse response = ConfirmedPlaceResponse.from(
				Place.of("Bowling", "Gangnam 1", "https://map.example.com/1"));

		assertThat(response.label()).isEqualTo("Bowling");
		assertThat(response.mapUrl()).isEqualTo("https://map.example.com/1");
	}

	@Test
	void 지도_링크가_없으면_주소로_검색_링크를_만든다() {
		assertThat(ConfirmedPlaceResponse.from(Place.of("Bowling", "Gangnam 1", null)).mapUrl())
				.isEqualTo(SEARCH_URL + "Gangnam%201");
	}

	@Test
	void 주소도_없으면_이름으로_검색_링크를_만든다() {
		assertThat(ConfirmedPlaceResponse.from(Place.of("Bowling", null, null)).mapUrl())
				.isEqualTo(SEARCH_URL + "Bowling");
	}

	@Test
	void 확정_전이면_null이다() {
		assertThat(ConfirmedPlaceResponse.from(null)).isNull();
	}
}
