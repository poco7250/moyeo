package com.poco7250.moyeo.place.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.dto.PlaceSearchItem;
import com.poco7250.moyeo.place.dto.PlaceSearchResponse;
import com.poco7250.moyeo.place.service.PlaceSearchService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PlaceSearchController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 검색 서비스는 목이다.
 */
@WebMvcTest(controllers = PlaceSearchController.class)
@Import(SecurityTestConfig.class)
class PlaceSearchControllerTest {

	private static final String URL = "/api/places/search";
	private static final long MEMBER_ID = 7L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	PlaceSearchService placeSearchService;

	@Test
	@DisplayName("검색 결과를 공통 응답으로 주고, 본문·헤더에 REST 키가 없다")
	void search() throws Exception {
		given(placeSearchService.search(MEMBER_ID, "강남 볼링", 1)).willReturn(new PlaceSearchResponse(
				List.of(new PlaceSearchItem("26338954", "강남볼링장", "볼링장", "역삼동 1", "테헤란로 1", null,
						"http://place.map.kakao.com/26338954")), 1, true));

		MockHttpServletResponse response = mockMvc.perform(get(URL).param("query", "강남 볼링").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.places[0].kakaoPlaceId").value("26338954"))
				.andExpect(jsonPath("$.data.places[0].name").value("강남볼링장"))
				.andExpect(jsonPath("$.data.places[0].roadAddress").value("테헤란로 1"))
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.isEnd").value(true))
				.andReturn().getResponse();

		assertThat(response.getContentAsString()).doesNotContain("test-local-rest-key", "KakaoAK");
		response.getHeaderNames().forEach(name ->
				assertThat(response.getHeaders(name)).noneMatch(value -> value.contains("test-local-rest-key")));
	}

	@Test
	@DisplayName("page를 넘기면 그 페이지로 검색한다")
	void searchWithPage() throws Exception {
		given(placeSearchService.search(MEMBER_ID, "볼링", 3)).willReturn(new PlaceSearchResponse(List.of(), 3, true));

		mockMvc.perform(get(URL).param("query", "볼링").param("page", "3").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(3));
	}

	@ParameterizedTest(name = "query=\"{0}\"")
	@ValueSource(strings = {"", "   "})
	@DisplayName("검색어가 비었거나 공백뿐이면 400 INVALID_INPUT")
	void blankQuery(String query) throws Exception {
		mockMvc.perform(get(URL).param("query", query).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(placeSearchService);
	}

	@Test
	@DisplayName("검색어 파라미터가 없으면 400 INVALID_INPUT")
	void missingQuery() throws Exception {
		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
	}

	@Test
	@DisplayName("검색어가 100자를 넘으면 400 INVALID_INPUT")
	void tooLongQuery() throws Exception {
		mockMvc.perform(get(URL).param("query", "가".repeat(101)).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(placeSearchService);
	}

	@ParameterizedTest(name = "page={0}")
	@ValueSource(strings = {"0", "46", "abc"})
	@DisplayName("page가 1~45 정수가 아니면 400 INVALID_INPUT")
	void invalidPage(String page) throws Exception {
		mockMvc.perform(get(URL).param("query", "볼링").param("page", page).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(placeSearchService);
	}

	@Test
	@DisplayName("비로그인이면 401 UNAUTHORIZED")
	void unauthorized() throws Exception {
		mockMvc.perform(get(URL).param("query", "볼링"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.name()));
	}

	@Test
	@DisplayName("카카오 장애면 503 PLACE_SEARCH_UNAVAILABLE(직접 입력 폴백 안내)")
	void unavailable() throws Exception {
		given(placeSearchService.search(anyLong(), anyString(), anyInt()))
				.willThrow(new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE));

		mockMvc.perform(get(URL).param("query", "볼링").cookie(accessCookie()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.PLACE_SEARCH_UNAVAILABLE.name()));
	}

	@Test
	@DisplayName("빈도 초과면 429 PLACE_SEARCH_RATE_LIMITED")
	void rateLimited() throws Exception {
		given(placeSearchService.search(anyLong(), anyString(), anyInt()))
				.willThrow(new BusinessException(ErrorCode.PLACE_SEARCH_RATE_LIMITED));

		mockMvc.perform(get(URL).param("query", "볼링").cookie(accessCookie()))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.PLACE_SEARCH_RATE_LIMITED.name()));
	}

	/**
	 * 로그인 회원의 access_token 쿠키를 만든다.
	 */
	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(MEMBER_ID));
	}
}
