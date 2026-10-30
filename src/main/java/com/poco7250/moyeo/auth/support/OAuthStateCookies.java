package com.poco7250.moyeo.auth.support;

import com.poco7250.moyeo.auth.config.TokenCookieProperties;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 카카오 로그인 시작~콜백 사이에만 쓰는 state·returnUrl 쿠키를 만들고 지운다.
 * state는 CSRF 방지용 난수로, 콜백의 state 파라미터와 비교한다. 값은 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OAuthStateCookies {

	public static final String STATE_COOKIE = "oauth_state";
	public static final String RETURN_URL_COOKIE = "oauth_return_url";
	// 로그인 시작·콜백 요청에만 실리도록 경로를 좁힌다
	public static final String PATH = "/api/auth/kakao";

	private static final Duration MAX_AGE = Duration.ofMinutes(10);
	private static final String SAME_SITE = "Lax";
	private static final int STATE_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final TokenCookieProperties cookieProperties;

	/**
	 * 32바이트 난수를 Base64URL(패딩 없음)로 인코딩한 state를 만든다.
	 */
	public String generateState() {
		byte[] bytes = new byte[STATE_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * state 쿠키를 만든다(Max-Age 10분).
	 */
	public ResponseCookie stateCookie(String state) {
		return build(STATE_COOKIE, state, MAX_AGE);
	}

	/**
	 * returnUrl 쿠키를 만든다. 쿠키 값에 못 쓰는 문자(;, 공백 등)가 있을 수 있어 URL 인코딩한다.
	 */
	public ResponseCookie returnUrlCookie(String returnUrl) {
		return build(RETURN_URL_COOKIE, URLEncoder.encode(returnUrl, StandardCharsets.UTF_8), MAX_AGE);
	}

	/**
	 * 브라우저의 state 쿠키를 지우는 쿠키를 만든다.
	 */
	public ResponseCookie expireStateCookie() {
		return build(STATE_COOKIE, "", Duration.ZERO);
	}

	/**
	 * 브라우저의 returnUrl 쿠키를 지우는 쿠키를 만든다.
	 */
	public ResponseCookie expireReturnUrlCookie() {
		return build(RETURN_URL_COOKIE, "", Duration.ZERO);
	}

	/**
	 * returnUrl 쿠키 값을 디코딩한다. 없거나 깨진 값이면 null(호출 쪽에서 기본 경로로 보낸다).
	 */
	public String decodeReturnUrl(String cookieValue) {
		if (cookieValue == null) {
			return null;
		}
		try {
			return URLDecoder.decode(cookieValue, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private ResponseCookie build(String name, String value, Duration maxAge) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(cookieProperties.secure())
				.sameSite(SAME_SITE)
				.path(PATH)
				.maxAge(maxAge);
		// 토큰 쿠키와 같은 규칙으로 domain을 붙여야 삭제 쿠키가 원래 쿠키를 덮는다
		if (StringUtils.hasText(cookieProperties.domain())) {
			builder.domain(cookieProperties.domain());
		}
		return builder.build();
	}
}
