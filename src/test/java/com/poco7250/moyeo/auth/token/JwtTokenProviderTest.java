package com.poco7250.moyeo.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.support.TestClocks;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * JwtTokenProvider를 고정 Clock으로 검증하는 단위 테스트.
 */
class JwtTokenProviderTest {

	private static final String SECRET = "test-jwt-secret-key-for-moyeo-must-be-32-bytes-or-more";
	private static final String OTHER_SECRET = "another-jwt-secret-key-which-is-also-32-bytes-long";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
	private static final Duration ACCESS_TTL = Duration.ofMinutes(30);

	private final JwtTokenProvider provider = providerAt(NOW);

	@Test
	@DisplayName("발급한 Access 토큰을 검증하면 같은 memberId가 나온다")
	void createAndParse() {
		String token = provider.createAccessToken(42L);

		assertThat(provider.parseMemberId(token)).isEqualTo(42L);
	}

	@Test
	@DisplayName("만료 직전 시각에는 아직 유효하다")
	void validJustBeforeExpiry() {
		String token = provider.createAccessToken(42L);

		JwtTokenProvider later = providerAt(NOW.plus(ACCESS_TTL).minusSeconds(1));

		assertThat(later.parseMemberId(token)).isEqualTo(42L);
	}

	@Test
	@DisplayName("TTL이 지난 시각에 검증하면 EXPIRED_TOKEN")
	void expiredToken() {
		String token = provider.createAccessToken(42L);

		JwtTokenProvider later = providerAt(NOW.plus(ACCESS_TTL).plusSeconds(1));

		assertErrorCode(() -> later.parseMemberId(token), ErrorCode.EXPIRED_TOKEN);
	}

	@Test
	@DisplayName("다른 키로 서명한 토큰은 INVALID_TOKEN")
	void forgedSignature() {
		String forged = Jwts.builder()
				.subject("42")
				.expiration(Date.from(TestClocks.fixed(NOW).instant().plus(ACCESS_TTL)))
				.signWith(Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8)))
				.compact();

		assertErrorCode(() -> provider.parseMemberId(forged), ErrorCode.INVALID_TOKEN);
	}

	@Test
	@DisplayName("subject가 숫자가 아닌 토큰은 INVALID_TOKEN")
	void nonNumericSubject() {
		String token = Jwts.builder()
				.subject("not-a-number")
				.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
				.compact();

		assertErrorCode(() -> provider.parseMemberId(token), ErrorCode.INVALID_TOKEN);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "not-a-jwt", "a.b.c"})
	@DisplayName("깨진 문자열·null·빈 값은 INVALID_TOKEN")
	void malformedToken(String token) {
		assertErrorCode(() -> provider.parseMemberId(token), ErrorCode.INVALID_TOKEN);
	}

	private static JwtTokenProvider providerAt(LocalDateTime dateTime) {
		Clock clock = TestClocks.fixed(dateTime);
		return new JwtTokenProvider(new JwtProperties(SECRET, ACCESS_TTL, Duration.ofDays(14)), clock);
	}

	private static void assertErrorCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(expected);
	}
}
