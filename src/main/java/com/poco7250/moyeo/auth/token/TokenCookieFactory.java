package com.poco7250.moyeo.auth.token;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.config.TokenCookieProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Access·Refresh 토큰을 담는 HttpOnly 쿠키를 만들고 지운다.
 * SameSite=Lax라 다른 사이트에서 보낸 POST·PUT·DELETE에는 쿠키가 실리지 않는다(CSRF 방어, SecurityConfig 참고).
 */
@Component
@RequiredArgsConstructor
public class TokenCookieFactory {

	public static final String ACCESS_TOKEN_COOKIE = "access_token";
	public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
	public static final String ACCESS_TOKEN_PATH = "/";
	// Refresh는 재발급·로그아웃 API에만 실리도록 경로를 좁힌다
	public static final String REFRESH_TOKEN_PATH = "/api/auth";

	private static final String SAME_SITE = "Lax";

	private final JwtProperties jwtProperties;
	private final TokenCookieProperties cookieProperties;

	/**
	 * Access 토큰 쿠키를 만든다(Path=/, Max-Age=Access TTL).
	 */
	public ResponseCookie accessCookie(String token) {
		return build(ACCESS_TOKEN_COOKIE, token, ACCESS_TOKEN_PATH, jwtProperties.accessTokenTtl());
	}

	/**
	 * Refresh 토큰 쿠키를 만든다(Path=/api/auth, Max-Age=Refresh TTL).
	 */
	public ResponseCookie refreshCookie(String token) {
		return build(REFRESH_TOKEN_COOKIE, token, REFRESH_TOKEN_PATH, jwtProperties.refreshTokenTtl());
	}

	/**
	 * 브라우저의 Access 토큰 쿠키를 지우는 쿠키를 만든다.
	 */
	public ResponseCookie expireAccessCookie() {
		return build(ACCESS_TOKEN_COOKIE, "", ACCESS_TOKEN_PATH, Duration.ZERO);
	}

	/**
	 * 브라우저의 Refresh 토큰 쿠키를 지우는 쿠키를 만든다.
	 */
	public ResponseCookie expireRefreshCookie() {
		return build(REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, Duration.ZERO);
	}

	private ResponseCookie build(String name, String value, String path, Duration maxAge) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(cookieProperties.secure())
				.sameSite(SAME_SITE)
				.path(path)
				.maxAge(maxAge);
		// 이름·경로·도메인이 같아야 삭제 쿠키가 원래 쿠키를 덮으므로 domain도 같은 규칙으로 붙인다
		if (StringUtils.hasText(cookieProperties.domain())) {
			builder.domain(cookieProperties.domain());
		}
		return builder.build();
	}
}
