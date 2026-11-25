package com.poco7250.moyeo.penalty.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PenaltyController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = PenaltyController.class)
@Import(SecurityTestConfig.class)
class PenaltyControllerTest {

	private static final Long HOST_ID = 4L;
	private static final Long GAME_ID = 300L;
	private static final String WAIVER_URL = "/api/games/300/penalty-waiver";
	private static final LocalDateTime WAIVED_AT = LocalDateTime.of(2026, 12, 20, 22, 0);
	private static final PenaltyConditionResponse APPLIED = new PenaltyConditionResponse(GAME_ID,
			PenaltyStatus.APPLIED, 10_000L, null, null, List.of(2L, 3L));
	private static final PenaltyConditionResponse WAIVED = new PenaltyConditionResponse(GAME_ID,
			PenaltyStatus.WAIVED, 10_000L, HOST_ID, WAIVED_AT, List.of(2L, 3L));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	PenaltyConditionService penaltyConditionService;

	@Test
	@DisplayName("면제는 면제자와 면제 시각을 돌려주고, 면제 취소는 APPLIED 상태를 돌려준다")
	void waiveAndUnwaive() throws Exception {
		given(penaltyConditionService.waive(GAME_ID, HOST_ID)).willReturn(WAIVED);
		given(penaltyConditionService.unwaive(GAME_ID, HOST_ID)).willReturn(APPLIED);

		mockMvc.perform(post(WAIVER_URL).cookie(loginCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("WAIVED"))
				.andExpect(jsonPath("$.data.penaltyAmount").value(10_000))
				.andExpect(jsonPath("$.data.waivedBy").value(4))
				.andExpect(jsonPath("$.data.waivedAt").exists());
		mockMvc.perform(delete(WAIVER_URL).cookie(loginCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPLIED"))
				.andExpect(jsonPath("$.data.waivedBy").doesNotExist());
	}

	@Test
	@DisplayName("서비스가 던진 403·409를 에러 코드와 함께 돌려준다")
	void serviceErrors() throws Exception {
		given(penaltyConditionService.waive(GAME_ID, HOST_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST))
				.willThrow(new BusinessException(ErrorCode.PENALTY_NOT_CONFIGURABLE));

		mockMvc.perform(post(WAIVER_URL).cookie(loginCookie())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_MEETING_HOST"));
		mockMvc.perform(post(WAIVER_URL).cookie(loginCookie())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PENALTY_NOT_CONFIGURABLE"));
	}

	@Test
	@DisplayName("금액 조건 API(penalty-rule)는 없어졌고, 로그인하지 않으면 면제 API는 401이다")
	void removedRuleAndUnauthenticated() throws Exception {
		mockMvc.perform(put("/api/games/300/penalty-rule").cookie(loginCookie()).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().is4xxClientError());
		mockMvc.perform(post(WAIVER_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(WAIVER_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(penaltyConditionService);
	}

	private Cookie loginCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(HOST_ID));
	}
}
