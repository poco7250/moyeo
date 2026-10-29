package com.poco7250.moyeo.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.config.TokenCookieProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenCookieFactoryTest {

	private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
			"test-jwt-secret-key-for-moyeo-must-be-32-bytes-or-more", Duration.ofMinutes(30), Duration.ofDays(14));

	private final TokenCookieFactory factory = factory("", true);

	@Test
	@DisplayName("Access 쿠키는 HttpOnly·Secure·SameSite=Lax, Path=/, Max-Age=1800이다")
	void accessCookie() {
		String cookie = factory.accessCookie("access-value").toString();

		assertThat(cookie)
				.startsWith("access_token=access-value")
				.contains("Path=/;", "Max-Age=1800", "HttpOnly", "Secure", "SameSite=Lax")
				.doesNotContain("Domain=");
	}

	@Test
	@DisplayName("Refresh 쿠키는 Path=/api/auth, Max-Age=14일(1209600초)이다")
	void refreshCookie() {
		String cookie = factory.refreshCookie("refresh-value").toString();

		assertThat(cookie)
				.startsWith("refresh_token=refresh-value")
				.contains("Path=/api/auth", "Max-Age=1209600", "HttpOnly", "Secure", "SameSite=Lax")
				.doesNotContain("Domain=");
	}

	@Test
	@DisplayName("만료 쿠키는 같은 이름·경로에 빈 값, Max-Age=0이다")
	void expireCookies() {
		String access = factory.expireAccessCookie().toString();
		String refresh = factory.expireRefreshCookie().toString();

		assertThat(access).startsWith("access_token=;").contains("Path=/;", "Max-Age=0", "HttpOnly");
		assertThat(refresh).startsWith("refresh_token=;").contains("Path=/api/auth", "Max-Age=0", "HttpOnly");
	}

	@Test
	@DisplayName("secure=false면 Secure 속성이 없다")
	void insecure() {
		String cookie = factory("", false).accessCookie("access-value").toString();

		assertThat(cookie).doesNotContain("Secure").contains("HttpOnly", "SameSite=Lax");
	}

	@Test
	@DisplayName("domain을 설정하면 삭제 쿠키를 포함해 Domain 속성이 붙는다")
	void withDomain() {
		TokenCookieFactory withDomain = factory("moyeo.example.com", true);

		assertThat(withDomain.refreshCookie("refresh-value").toString()).contains("Domain=moyeo.example.com");
		assertThat(withDomain.expireRefreshCookie().toString()).contains("Domain=moyeo.example.com");
	}

	private static TokenCookieFactory factory(String domain, boolean secure) {
		return new TokenCookieFactory(JWT_PROPERTIES, new TokenCookieProperties(domain, secure));
	}
}
