package com.poco7250.moyeo.auth.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.config.TokenCookieProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * OAuthStateCookies 단위 테스트.
 */
class OAuthStateCookiesTest {

	private final OAuthStateCookies cookies = new OAuthStateCookies(new TokenCookieProperties("", true));

	@Test
	@DisplayName("state는 32바이트 난수의 Base64URL(43자)이고 매번 다르다")
	void generateState() {
		String state = cookies.generateState();

		assertThat(state).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(cookies.generateState()).isNotEqualTo(state);
	}

	@Test
	@DisplayName("state 쿠키는 HttpOnly·Secure·Lax, Path=/api/auth/kakao, Max-Age 10분이다")
	void stateCookie() {
		ResponseCookie cookie = cookies.stateCookie("abc");

		assertThat(cookie.getName()).isEqualTo(OAuthStateCookies.STATE_COOKIE);
		assertThat(cookie.getValue()).isEqualTo("abc");
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.isSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Lax");
		assertThat(cookie.getPath()).isEqualTo(OAuthStateCookies.PATH);
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(10));
		assertThat(cookie.getDomain()).isNull();
	}

	@Test
	@DisplayName("domain·secure 설정을 그대로 반영한다")
	void appliesCookieProperties() {
		OAuthStateCookies custom = new OAuthStateCookies(new TokenCookieProperties("moyeo.app", false));

		ResponseCookie cookie = custom.expireStateCookie();

		assertThat(cookie.getDomain()).isEqualTo("moyeo.app");
		assertThat(cookie.isSecure()).isFalse();
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
	}

	@Test
	@DisplayName("returnUrl은 URL 인코딩해 담고, 디코딩하면 원래 값이 나온다")
	void returnUrlRoundTrip() {
		String returnUrl = "/invites/abc?from=kakao&x=a b;c";

		ResponseCookie cookie = cookies.returnUrlCookie(returnUrl);

		assertThat(cookie.getValue()).doesNotContain(";", " ");
		assertThat(cookies.decodeReturnUrl(cookie.getValue())).isEqualTo(returnUrl);
	}

	@Test
	@DisplayName("returnUrl 쿠키 값이 없거나 깨졌으면 null을 돌려준다")
	void decodeInvalid() {
		assertThat(cookies.decodeReturnUrl(null)).isNull();
		assertThat(cookies.decodeReturnUrl("%zz")).isNull();
	}

	@Test
	@DisplayName("삭제 쿠키는 이름·경로가 같고 Max-Age=0이다")
	void expireCookies() {
		assertThat(cookies.expireReturnUrlCookie().getName()).isEqualTo(OAuthStateCookies.RETURN_URL_COOKIE);
		assertThat(cookies.expireReturnUrlCookie().getPath()).isEqualTo(OAuthStateCookies.PATH);
		assertThat(cookies.expireReturnUrlCookie().getMaxAge()).isEqualTo(Duration.ZERO);
	}
}
