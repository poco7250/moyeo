package com.poco7250.moyeo.auth.controller;

import com.poco7250.moyeo.auth.client.KakaoOAuthClient;
import com.poco7250.moyeo.auth.config.AuthRedirectProperties;
import com.poco7250.moyeo.auth.dto.TokenPair;
import com.poco7250.moyeo.auth.service.AuthService;
import com.poco7250.moyeo.auth.service.TokenService;
import com.poco7250.moyeo.auth.support.OAuthStateCookies;
import com.poco7250.moyeo.auth.support.ReturnUrlValidator;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 로그인·콜백, 토큰 재발급, 로그아웃 API.
 * 토큰은 응답 본문이 아니라 HttpOnly 쿠키로만 내려준다. code·state·토큰은 로그에 남기지 않는다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final TokenService tokenService;
	private final KakaoOAuthClient kakaoOAuthClient;
	private final OAuthStateCookies oauthStateCookies;
	private final TokenCookieFactory tokenCookieFactory;
	private final ReturnUrlValidator returnUrlValidator;
	private final AuthRedirectProperties redirectProperties;

	/**
	 * 카카오 로그인 화면으로 보낸다. state와 로그인 후 돌아갈 경로는 쿠키에 담아 콜백에서 꺼낸다.
	 */
	@GetMapping("/kakao/login")
	public ResponseEntity<Void> login(@RequestParam(required = false) String returnUrl) {
		String state = oauthStateCookies.generateState();
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(kakaoOAuthClient.buildAuthorizeUrl(state)))
				.header(HttpHeaders.SET_COOKIE, oauthStateCookies.stateCookie(state).toString())
				.header(HttpHeaders.SET_COOKIE,
						oauthStateCookies.returnUrlCookie(returnUrlValidator.sanitize(returnUrl)).toString())
				.build();
	}

	/**
	 * 카카오 로그인 콜백. state를 검증하고 토큰 쿠키를 내려준 뒤 원래 경로로 보낸다.
	 *
	 * @throws BusinessException 사용자가 동의를 취소했거나 code가 없으면 KAKAO_AUTH_FAILED, state가 다르면 INVALID_OAUTH_STATE
	 */
	@GetMapping("/kakao/callback")
	public ResponseEntity<Void> callback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@CookieValue(name = OAuthStateCookies.STATE_COOKIE, required = false) String stateCookie,
			@CookieValue(name = OAuthStateCookies.RETURN_URL_COOKIE, required = false) String returnUrlCookie) {
		verifyState(state, stateCookie);
		if (error != null || !StringUtils.hasText(code)) {
			throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
		}
		TokenPair tokens = authService.loginWithKakao(code);
		String returnUrl = returnUrlValidator.sanitize(oauthStateCookies.decodeReturnUrl(returnUrlCookie));
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(redirectProperties.frontendBaseUrl() + returnUrl))
				.header(HttpHeaders.SET_COOKIE, tokenCookieFactory.accessCookie(tokens.accessToken()).toString())
				.header(HttpHeaders.SET_COOKIE, tokenCookieFactory.refreshCookie(tokens.refreshToken()).toString())
				.header(HttpHeaders.SET_COOKIE, oauthStateCookies.expireStateCookie().toString())
				.header(HttpHeaders.SET_COOKIE, oauthStateCookies.expireReturnUrlCookie().toString())
				.build();
	}

	/**
	 * Refresh 토큰 쿠키로 Access·Refresh를 새로 발급한다(회전).
	 *
	 * @throws BusinessException 쿠키가 없거나 만료·회전된 토큰이면 INVALID_REFRESH_TOKEN
	 */
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<Void>> refresh(
			@CookieValue(name = TokenCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
		TokenPair tokens = tokenService.reissue(refreshToken);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, tokenCookieFactory.accessCookie(tokens.accessToken()).toString())
				.header(HttpHeaders.SET_COOKIE, tokenCookieFactory.refreshCookie(tokens.refreshToken()).toString())
				.body(ApiResponse.ok());
	}

	/**
	 * Refresh 토큰을 폐기하고 토큰 쿠키를 지운다. 로그인 상태가 아니어도 성공으로 응답한다.
	 */
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
			@CookieValue(name = TokenCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
		tokenService.revoke(refreshToken);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, tokenCookieFactory.expireAccessCookie().toString())
				.header(HttpHeaders.SET_COOKIE, tokenCookieFactory.expireRefreshCookie().toString())
				.body(ApiResponse.ok());
	}

	/**
	 * 콜백의 state 파라미터가 로그인 시작 때 내려준 쿠키 값과 같은지 본다(타이밍 공격을 피하려고 상수 시간 비교).
	 */
	private void verifyState(String state, String stateCookie) {
		if (!StringUtils.hasText(state) || !StringUtils.hasText(stateCookie)
				|| !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
						stateCookie.getBytes(StandardCharsets.UTF_8))) {
			throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
		}
	}
}
