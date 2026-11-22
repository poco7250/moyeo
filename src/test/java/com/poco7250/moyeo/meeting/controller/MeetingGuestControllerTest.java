package com.poco7250.moyeo.meeting.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.dto.MeetingGuestResponse;
import com.poco7250.moyeo.meeting.service.MeetingGuestService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MeetingGuestController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = MeetingGuestController.class)
@Import(SecurityTestConfig.class)
class MeetingGuestControllerTest {

	private static final String URL = "/api/meetings/10/guests";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	MeetingGuestService meetingGuestService;

	@Test
	@DisplayName("게스트를 추가하면 201과 게스트 id·이름을 공통 응답 래퍼로 준다")
	void add() throws Exception {
		given(meetingGuestService.add(10L, 7L, "민수 친구")).willReturn(new MeetingGuestResponse(50L, "민수 친구"));

		mockMvc.perform(post(URL).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"민수 친구\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.guestId").value(50))
				.andExpect(jsonPath("$.data.name").value("민수 친구"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"name\":\"  \"}", "{\"name\":\"123456789012345678901\"}"})
	@DisplayName("이름이 없거나 공백뿐이거나 20자를 넘으면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void invalidName(String body) throws Exception {
		mockMvc.perform(post(URL).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'name')]").exists());
		verifyNoInteractions(meetingGuestService);
	}

	@Test
	@DisplayName("호스트 권한이 없으면 403, 같은 이름이면 409를 그대로 전달한다")
	void passesErrors() throws Exception {
		given(meetingGuestService.add(10L, 7L, "민수"))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));
		given(meetingGuestService.add(10L, 7L, "영희"))
				.willThrow(new BusinessException(ErrorCode.MEETING_GUEST_DUPLICATED));

		mockMvc.perform(post(URL).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"민수\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_MEETING_HOST"));
		mockMvc.perform(post(URL).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"영희\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETING_GUEST_DUPLICATED"));
	}

	@Test
	@DisplayName("게스트를 삭제하면 200이고, 게임에 참가한 게스트면 409 MEETING_GUEST_IN_USE")
	void remove() throws Exception {
		mockMvc.perform(delete(URL + "/50").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		verify(meetingGuestService).remove(10L, 7L, 50L);

		willThrow(new BusinessException(ErrorCode.MEETING_GUEST_IN_USE)).given(meetingGuestService)
				.remove(10L, 7L, 51L);
		mockMvc.perform(delete(URL + "/51").cookie(accessCookie()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETING_GUEST_IN_USE"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"민수\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(URL + "/50")).andExpect(status().isUnauthorized());
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(7L));
	}
}
