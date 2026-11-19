package com.poco7250.moyeo.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.dto.FavoritePlaceResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RecentPlaceResponse;
import com.poco7250.moyeo.place.service.GroupPlaceService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GroupPlaceController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = GroupPlaceController.class)
@Import(SecurityTestConfig.class)
class GroupPlaceControllerTest {

	private static final String FAVORITES = "/api/groups/5/favorite-places";
	private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 1, 18, 0);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	GroupPlaceService groupPlaceService;

	@Test
	@DisplayName("단골 장소 목록을 공통 응답 래퍼로 주고, 지도 링크가 없으면 카카오맵 검색 링크를 준다")
	void getFavorites() throws Exception {
		given(groupPlaceService.listFavorites(5L, 8L)).willReturn(List.of(new FavoritePlaceResponse(3L, "강남 볼링장",
				"서울 강남구", null, "https://map.kakao.com/link/search/%EC%84%9C%EC%9A%B8%20%EA%B0%95%EB%82%A8%EA%B5%AC", 8L, AT)));

		mockMvc.perform(get(FAVORITES).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data[0].id").value(3))
				.andExpect(jsonPath("$.data[0].label").value("강남 볼링장"))
				.andExpect(jsonPath("$.data[0].manualAddress").value("서울 강남구"))
				.andExpect(jsonPath("$.data[0].mapUrl").value("https://map.kakao.com/link/search/%EC%84%9C%EC%9A%B8%20%EA%B0%95%EB%82%A8%EA%B5%AC"))
				.andExpect(jsonPath("$.data[0].createdByMemberId").value(8))
				.andExpect(jsonPath("$.data[0].createdAt").value("2026-11-01T18:00:00"));
	}

	@Test
	@DisplayName("단골 장소를 저장하면 201과 저장된 장소를 주고, 입력 앞뒤 공백은 지워 서비스에 넘긴다")
	void addFavorite() throws Exception {
		PlaceInput expected = new PlaceInput("강남 볼링장", null, "https://place.map.kakao.com/1");
		given(groupPlaceService.addFavorite(5L, 8L, expected)).willReturn(new FavoritePlaceResponse(3L, "강남 볼링장",
				null, "https://place.map.kakao.com/1", "https://place.map.kakao.com/1", 8L, AT));

		mockMvc.perform(post(FAVORITES).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"label":"  강남 볼링장 ","manualAddress":"  ","manualMapUrl":"https://place.map.kakao.com/1"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").value(3))
				.andExpect(jsonPath("$.data.manualAddress").doesNotExist());
		verify(groupPlaceService).addFavorite(5L, 8L, expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{\"label\":\"  \"}|label",
			"{\"label\":\"가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가\"}|label",
			"{\"label\":\"볼링장\",\"manualMapUrl\":\"http://map.kakao.com\"}|manualMapUrl",
			"{\"label\":\"볼링장\",\"manualMapUrl\":\"javascript:alert(1)\"}|manualMapUrl"})
	@DisplayName("이름이 비거나 50자를 넘거나 지도 링크가 https가 아니면 400 필드 에러이고 서비스를 호출하지 않는다")
	void invalidFavorite(String body, String field) throws Exception {
		mockMvc.perform(post(FAVORITES).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value(field));
		verifyNoInteractions(groupPlaceService);
	}

	@ParameterizedTest
	@CsvSource({"FAVORITE_PLACE_DUPLICATED, 409", "FAVORITE_PLACE_LIMIT, 409", "NOT_GROUP_MEMBER, 403"})
	@DisplayName("저장 거절(중복·개수 초과·비멤버)을 그대로 전달한다")
	void addRejected(ErrorCode code, int httpStatus) throws Exception {
		given(groupPlaceService.addFavorite(eq(5L), eq(8L), any())).willThrow(new BusinessException(code));

		mockMvc.perform(post(FAVORITES).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"label\":\"볼링장\"}"))
				.andExpect(status().is(httpStatus))
				.andExpect(jsonPath("$.error.code").value(code.name()));
	}

	@Test
	@DisplayName("단골 장소를 지우면 200, 없는 단골이면 404 FAVORITE_PLACE_NOT_FOUND")
	void deleteFavorite() throws Exception {
		mockMvc.perform(delete(FAVORITES + "/3").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		verify(groupPlaceService).deleteFavorite(5L, 3L, 8L);

		willThrow(new BusinessException(ErrorCode.FAVORITE_PLACE_NOT_FOUND))
				.given(groupPlaceService).deleteFavorite(5L, 4L, 8L);
		mockMvc.perform(delete(FAVORITES + "/4").cookie(accessCookie()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("FAVORITE_PLACE_NOT_FOUND"));
	}

	@Test
	@DisplayName("지난 장소 목록은 마지막 사용 시각·사용 횟수와 함께 준다")
	void getRecentPlaces() throws Exception {
		given(groupPlaceService.listRecentPlaces(5L, 8L)).willReturn(List.of(
				new RecentPlaceResponse("강남 고깃집", null, null, "https://map.kakao.com/link/search/강남 고깃집", AT, 3)));

		mockMvc.perform(get("/api/groups/5/recent-places").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].label").value("강남 고깃집"))
				.andExpect(jsonPath("$.data[0].lastUsedAt").value("2026-11-01T18:00:00"))
				.andExpect(jsonPath("$.data[0].useCount").value(3));
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() throws Exception {
		given(groupPlaceService.listRecentPlaces(5L, 8L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/groups/5/recent-places").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(get(FAVORITES)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/groups/5/recent-places")).andExpect(status().isUnauthorized());
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
