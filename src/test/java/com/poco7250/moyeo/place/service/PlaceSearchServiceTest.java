package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.client.KakaoLocalClient;
import com.poco7250.moyeo.place.config.PlaceSearchProperties;
import com.poco7250.moyeo.place.dto.KakaoLocalSearchResponse;
import com.poco7250.moyeo.place.dto.PlaceSearchItem;
import com.poco7250.moyeo.place.dto.PlaceSearchResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

	private static final KakaoLocalSearchResponse.Document BOWLING = new KakaoLocalSearchResponse.Document(
			"26338954", "강남볼링장", "볼링장", "역삼동 1", "테헤란로 1", "02-000-0000", "http://place.map.kakao.com/26338954");

	@Mock
	KakaoLocalClient kakaoLocalClient;

	@Mock
	PlaceSearchRateLimiter rateLimiter;

	private PlaceSearchService service;

	@BeforeEach
	void setUp() {
		// 의존성이 클라이언트·빈도 제한·설정뿐이라 검색 결과를 저장·캐시할 경로가 없다
		PlaceSearchProperties properties = new PlaceSearchProperties(15,
				new PlaceSearchProperties.RateLimit(30, Duration.ofMinutes(1)));
		service = new PlaceSearchService(kakaoLocalClient, rateLimiter, properties);
	}

	@Test
	@DisplayName("카카오 결과를 표시용 DTO로 그대로 옮긴다")
	void mapsResult() {
		given(kakaoLocalClient.searchKeyword("강남 볼링", 1, 15)).willReturn(
				new KakaoLocalSearchResponse(List.of(BOWLING), new KakaoLocalSearchResponse.Meta(false)));

		PlaceSearchResponse response = service.search(1L, "  강남 볼링 ", 1);

		assertThat(response.places()).containsExactly(new PlaceSearchItem("26338954", "강남볼링장", "볼링장",
				"역삼동 1", "테헤란로 1", "02-000-0000", "http://place.map.kakao.com/26338954"));
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.isEnd()).isFalse();
	}

	@Test
	@DisplayName("같은 검색어로 두 번 검색하면 카카오를 두 번 호출한다(캐시 없음)")
	void noCache() {
		given(kakaoLocalClient.searchKeyword("강남 볼링", 1, 15)).willReturn(
				new KakaoLocalSearchResponse(List.of(BOWLING), new KakaoLocalSearchResponse.Meta(true)));

		service.search(1L, "강남 볼링", 1);
		service.search(1L, "강남 볼링", 1);

		verify(kakaoLocalClient, times(2)).searchKeyword("강남 볼링", 1, 15);
		verify(rateLimiter, times(2)).acquire(1L);
	}

	@Test
	@DisplayName("빈도 제한에 걸리면 카카오를 호출하지 않는다")
	void rateLimitedSkipsKakao() {
		willThrow(new BusinessException(ErrorCode.PLACE_SEARCH_RATE_LIMITED)).given(rateLimiter).acquire(1L);

		assertThatThrownBy(() -> service.search(1L, "강남 볼링", 1)).isInstanceOf(BusinessException.class);

		verify(kakaoLocalClient, never()).searchKeyword(anyString(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("결과·메타가 비어 있으면 빈 마지막 페이지로 준다")
	void emptyResult() {
		given(kakaoLocalClient.searchKeyword("없는곳", 1, 15)).willReturn(new KakaoLocalSearchResponse(null, null));

		PlaceSearchResponse response = service.search(1L, "없는곳", 1);

		assertThat(response.places()).isEmpty();
		assertThat(response.isEnd()).isTrue();
	}
}
