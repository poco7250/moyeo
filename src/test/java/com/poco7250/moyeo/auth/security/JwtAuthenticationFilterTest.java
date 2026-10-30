package com.poco7250.moyeo.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.support.TestClocks;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JwtAuthenticationFilter를 고정 Clock과 실제 JwtTokenProvider로 검증하는 단위 테스트.
 */
class JwtAuthenticationFilterTest {

	private static final String SECRET = "test-jwt-secret-key-for-moyeo-must-be-32-bytes-or-more";
	private static final String OTHER_SECRET = "another-jwt-secret-key-which-is-also-32-bytes-long";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
	private static final Duration ACCESS_TTL = Duration.ofMinutes(30);

	private final JwtTokenProvider provider = providerAt(SECRET, NOW);
	private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

	private final MockHttpServletRequest request = new MockHttpServletRequest();
	private final MockHttpServletResponse response = new MockHttpServletResponse();
	private final MockFilterChain chain = new MockFilterChain();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("유효한 토큰이면 principal이 memberId인 인증을 설정한다")
	void validToken() throws Exception {
		request.setCookies(accessCookie(provider.createAccessToken(42L)));

		filter.doFilter(request, response, chain);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getPrincipal()).isEqualTo(42L);
		assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	@DisplayName("만료된 토큰이면 인증 없이 EXPIRED_TOKEN을 attribute에 남긴다")
	void expiredToken() throws Exception {
		String token = providerAt(SECRET, NOW.minus(ACCESS_TTL).minusSeconds(1)).createAccessToken(42L);
		request.setCookies(accessCookie(token));

		filter.doFilter(request, response, chain);

		assertUnauthenticated();
		assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isEqualTo(ErrorCode.EXPIRED_TOKEN);
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰이면 인증 없이 INVALID_TOKEN을 attribute에 남긴다")
	void forgedToken() throws Exception {
		request.setCookies(accessCookie(providerAt(OTHER_SECRET, NOW).createAccessToken(42L)));

		filter.doFilter(request, response, chain);

		assertUnauthenticated();
		assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isEqualTo(ErrorCode.INVALID_TOKEN);
	}

	@Test
	@DisplayName("쿠키가 없으면 인증도 attribute도 없이 다음 필터로 넘긴다")
	void noCookie() throws Exception {
		filter.doFilter(request, response, chain);

		assertUnauthenticated();
		assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isNull();
	}

	@Test
	@DisplayName("쿠키 값이 비어 있으면 검증하지 않고 다음 필터로 넘긴다")
	void emptyCookie() throws Exception {
		request.setCookies(accessCookie(""));

		filter.doFilter(request, response, chain);

		assertUnauthenticated();
		assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isNull();
	}

	private void assertUnauthenticated() {
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	private static Cookie accessCookie(String token) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, token);
	}

	private static JwtTokenProvider providerAt(String secret, LocalDateTime dateTime) {
		return new JwtTokenProvider(new JwtProperties(secret, ACCESS_TTL, Duration.ofDays(14)),
				TestClocks.fixed(dateTime));
	}
}
