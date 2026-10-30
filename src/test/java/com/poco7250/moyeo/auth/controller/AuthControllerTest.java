package com.poco7250.moyeo.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.client.KakaoOAuthClient;
import com.poco7250.moyeo.auth.config.AuthRedirectProperties;
import com.poco7250.moyeo.auth.config.TokenCookieProperties;
import com.poco7250.moyeo.auth.dto.TokenPair;
import com.poco7250.moyeo.auth.service.AuthService;
import com.poco7250.moyeo.auth.service.TokenService;
import com.poco7250.moyeo.auth.support.OAuthStateCookies;
import com.poco7250.moyeo.auth.support.ReturnUrlValidator;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AuthController 슬라이스 테스트. 실제 시큐리티 체인·쿠키 팩토리를 쓰고, 카카오·토큰 서비스는 목이다.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityTestConfig.class, AuthControllerTest.CookieConfig.class})
class AuthControllerTest {

	private static final String LOGIN_URL = "/api/auth/kakao/login";
	private static final String CALLBACK_URL = "/api/auth/kakao/callback";
	private static final String STATE = "state-value";
	private static final TokenPair TOKENS = new TokenPair("new-access", "new-refresh");

	@TestConfiguration
	@EnableConfigurationProperties({TokenCookieProperties.class, AuthRedirectProperties.class})
	@Import({TokenCookieFactory.class, OAuthStateCookies.class, ReturnUrlValidator.class})
	static class CookieConfig {
	}

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthService authService;

	@MockitoBean
	TokenService tokenService;

	@MockitoBean
	KakaoOAuthClient kakaoOAuthClient;

	@Test
	@DisplayName("로그인 시작: 카카오 인가 URL로 302, state·returnUrl 쿠키를 내린다")
	void login() throws Exception {
		given(kakaoOAuthClient.buildAuthorizeUrl(anyString()))
				.willAnswer(inv -> "https://kauth.kakao.com/oauth/authorize?state=" + inv.getArgument(0));

		MvcResult result = mockMvc.perform(get(LOGIN_URL).param("returnUrl", "/invites/abc"))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, containsString("state=")))
				.andReturn();

		String stateCookie = findCookie(result, OAuthStateCookies.STATE_COOKIE);
		String state = cookieValue(stateCookie);
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("state=" + state);
		assertThat(stateCookie).contains("HttpOnly", "Path=/api/auth/kakao", "SameSite=Lax");
		assertThat(cookieValue(findCookie(result, OAuthStateCookies.RETURN_URL_COOKIE)))
				.isEqualTo(encode("/invites/abc"));
	}

	@Test
	@DisplayName("로그인 시작: 외부 도메인 returnUrl은 기본 경로(/)로 바꿔 저장한다")
	void loginWithExternalReturnUrl() throws Exception {
		given(kakaoOAuthClient.buildAuthorizeUrl(anyString())).willReturn("https://kauth.kakao.com/oauth/authorize");

		MvcResult result = mockMvc.perform(get(LOGIN_URL).param("returnUrl", "https://evil.com/phish"))
				.andExpect(status().isFound())
				.andReturn();

		assertThat(cookieValue(findCookie(result, OAuthStateCookies.RETURN_URL_COOKIE))).isEqualTo(encode("/"));
	}

	@Test
	@DisplayName("콜백 성공: HttpOnly 토큰 쿠키를 내리고 state 쿠키를 지운 뒤 returnUrl로 302")
	void callbackSuccess() throws Exception {
		given(authService.loginWithKakao("kakao-code")).willReturn(TOKENS);

		MvcResult result = performCallback(STATE, STATE, encode("/invites/abc"))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "/invites/abc"))
				.andReturn();

		assertThat(findCookie(result, TokenCookieFactory.ACCESS_TOKEN_COOKIE))
				.startsWith("access_token=new-access").contains("HttpOnly", "Path=/");
		assertThat(findCookie(result, TokenCookieFactory.REFRESH_TOKEN_COOKIE))
				.startsWith("refresh_token=new-refresh").contains("HttpOnly", "Path=/api/auth");
		assertThat(findCookie(result, OAuthStateCookies.STATE_COOKIE)).contains("Max-Age=0");
		assertThat(findCookie(result, OAuthStateCookies.RETURN_URL_COOKIE)).contains("Max-Age=0");
	}

	@Test
	@DisplayName("콜백: state가 쿠키와 다르면 401 INVALID_OAUTH_STATE, 로그인하지 않는다")
	void callbackStateMismatch() throws Exception {
		expectError(performCallback("other-state", STATE, null), ErrorCode.INVALID_OAUTH_STATE);
		verify(authService, never()).loginWithKakao(any());
	}

	@Test
	@DisplayName("콜백: state 쿠키가 없으면 401 INVALID_OAUTH_STATE")
	void callbackWithoutStateCookie() throws Exception {
		expectError(performCallback(STATE, null, null), ErrorCode.INVALID_OAUTH_STATE);
		verify(authService, never()).loginWithKakao(any());
	}

	@Test
	@DisplayName("콜백: 사용자가 동의를 취소하면(error 파라미터) 401 KAKAO_AUTH_FAILED")
	void callbackWithKakaoError() throws Exception {
		ResultActions actions = mockMvc.perform(get(CALLBACK_URL)
				.param("error", "access_denied")
				.param("state", STATE)
				.cookie(new Cookie(OAuthStateCookies.STATE_COOKIE, STATE)));

		expectError(actions, ErrorCode.KAKAO_AUTH_FAILED);
		verify(authService, never()).loginWithKakao(any());
	}

	@Test
	@DisplayName("콜백: returnUrl 쿠키가 외부 주소로 조작됐으면 기본 경로(/)로 302")
	void callbackWithTamperedReturnUrl() throws Exception {
		given(authService.loginWithKakao("kakao-code")).willReturn(TOKENS);

		performCallback(STATE, STATE, encode("//evil.com"))
				.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "/"));
	}

	@Test
	@DisplayName("재발급: Refresh 쿠키로 새 토큰 쿠키 2개를 내리고 success=true")
	void refresh() throws Exception {
		given(tokenService.reissue("old-refresh")).willReturn(TOKENS);

		MvcResult result = mockMvc.perform(post("/api/auth/refresh")
						.cookie(new Cookie(TokenCookieFactory.REFRESH_TOKEN_COOKIE, "old-refresh")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andReturn();

		assertThat(findCookie(result, TokenCookieFactory.ACCESS_TOKEN_COOKIE)).startsWith("access_token=new-access");
		assertThat(findCookie(result, TokenCookieFactory.REFRESH_TOKEN_COOKIE)).startsWith("refresh_token=new-refresh");
	}

	@Test
	@DisplayName("재발급: Refresh가 유효하지 않으면 401 INVALID_REFRESH_TOKEN")
	void refreshInvalid() throws Exception {
		given(tokenService.reissue(null)).willThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		expectError(mockMvc.perform(post("/api/auth/refresh")), ErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	@DisplayName("로그아웃: Refresh를 폐기하고 토큰 쿠키 2개를 Max-Age=0으로 지운다")
	void logout() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/logout")
						.cookie(new Cookie(TokenCookieFactory.REFRESH_TOKEN_COOKIE, "old-refresh")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andReturn();

		verify(tokenService).revoke("old-refresh");
		assertThat(findCookie(result, TokenCookieFactory.ACCESS_TOKEN_COOKIE)).contains("Max-Age=0");
		assertThat(findCookie(result, TokenCookieFactory.REFRESH_TOKEN_COOKIE)).contains("Max-Age=0");
	}

	private ResultActions performCallback(String state, String stateCookie, String returnUrlCookie) throws Exception {
		var request = get(CALLBACK_URL).param("code", "kakao-code").param("state", state);
		if (stateCookie != null) {
			request.cookie(new Cookie(OAuthStateCookies.STATE_COOKIE, stateCookie));
		}
		if (returnUrlCookie != null) {
			request.cookie(new Cookie(OAuthStateCookies.RETURN_URL_COOKIE, returnUrlCookie));
		}
		return mockMvc.perform(request);
	}

	private void expectError(ResultActions actions, ErrorCode errorCode) throws Exception {
		actions.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(errorCode.name()));
	}

	private String findCookie(MvcResult result, String name) {
		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		return setCookies.stream()
				.filter(cookie -> cookie.startsWith(name + "="))
				.findFirst()
				.orElseThrow(() -> new AssertionError(name + " 쿠키가 없음: " + setCookies));
	}

	private String cookieValue(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
