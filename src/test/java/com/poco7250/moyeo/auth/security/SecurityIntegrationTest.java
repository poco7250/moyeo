package com.poco7250.moyeo.auth.security;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.support.SecurityTestConfig;
import com.poco7250.moyeo.support.TestClocks;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 SecurityConfig 체인으로 쿠키 인증·401 JSON·공개 경로·@LoginMember 주입을 검증한다.
 */
@WebMvcTest(controllers = SecurityTestController.class)
@Import(SecurityTestConfig.class)
class SecurityIntegrationTest {

	private static final String ME_URL = "/test/security/me";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	JwtProperties jwtProperties;

	@Test
	@DisplayName("유효한 access_token 쿠키면 200이고 @LoginMember로 memberId를 받는다")
	void authenticated() throws Exception {
		mockMvc.perform(get(ME_URL).cookie(accessCookie(jwtTokenProvider.createAccessToken(7L))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(7));
	}

	@Test
	@DisplayName("쿠키가 없으면 401 UNAUTHORIZED JSON을 준다")
	void noCookie() throws Exception {
		expectUnauthorized(mockMvc.perform(get(ME_URL)), ErrorCode.UNAUTHORIZED);
	}

	@Test
	@DisplayName("만료된 토큰이면 401 EXPIRED_TOKEN JSON을 준다")
	void expiredToken() throws Exception {
		LocalDateTime past = LocalDateTime.now(TestClocks.SEOUL).minusDays(1);
		String token = new JwtTokenProvider(jwtProperties, TestClocks.fixed(past)).createAccessToken(7L);

		expectUnauthorized(mockMvc.perform(get(ME_URL).cookie(accessCookie(token))), ErrorCode.EXPIRED_TOKEN);
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰이면 401 INVALID_TOKEN JSON을 준다")
	void forgedToken() throws Exception {
		JwtProperties other = new JwtProperties("another-jwt-secret-key-which-is-also-32-bytes-long",
				Duration.ofMinutes(30), Duration.ofDays(14));
		String token = new JwtTokenProvider(other, TestClocks.fixed(LocalDateTime.now(TestClocks.SEOUL)))
				.createAccessToken(7L);

		expectUnauthorized(mockMvc.perform(get(ME_URL).cookie(accessCookie(token))), ErrorCode.INVALID_TOKEN);
	}

	@Test
	@DisplayName("공개 경로는 쿠키가 없어도 접근할 수 있다")
	void publicPath() throws Exception {
		mockMvc.perform(get("/api/invites/abc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("abc"));
	}

	@Test
	@DisplayName("공개 경로는 만료된 토큰이 실려 있어도 401로 막지 않는다")
	void publicPathWithExpiredToken() throws Exception {
		LocalDateTime past = LocalDateTime.now(TestClocks.SEOUL).minusDays(1);
		String token = new JwtTokenProvider(jwtProperties, TestClocks.fixed(past)).createAccessToken(7L);

		mockMvc.perform(get("/api/invites/abc").cookie(accessCookie(token)))
				.andExpect(status().isOk());
	}

	private void expectUnauthorized(ResultActions result, ErrorCode errorCode) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.error.code").value(errorCode.name()))
				.andExpect(jsonPath("$.error.message").value(errorCode.getMessage()));
	}

	private static Cookie accessCookie(String token) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, token);
	}
}
