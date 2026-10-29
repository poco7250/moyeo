package com.poco7250.moyeo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class JwtPropertiesTest {

	private static final String SECRET = "test-jwt-secret-key-for-moyeo-must-be-32-bytes-or-more";

	@Test
	@DisplayName("secret만 넣으면 TTL은 기본값(Access 30분, Refresh 14일)으로 바인딩된다")
	void defaultTtl() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("auth.jwt.secret", SECRET)));

		JwtProperties properties = binder.bind("auth.jwt", JwtProperties.class).get();

		assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
		assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(14));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"short-secret", "0123456789abcdef0123456789abcde"})
	@DisplayName("secret이 32바이트 미만이면 예외가 난다")
	void shortSecret(String secret) {
		assertThatThrownBy(() -> new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("secret이 정확히 32바이트면 통과한다")
	void exactly32Bytes() {
		JwtProperties properties = new JwtProperties(
				"0123456789abcdef0123456789abcdef", Duration.ofMinutes(30), Duration.ofDays(14));

		assertThat(properties.secret()).hasSize(32);
	}

	@Test
	@DisplayName("toString에 secret 원문이 없다")
	void toStringMasksSecret() {
		JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));

		assertThat(properties.toString()).doesNotContain(SECRET).contains("accessTokenTtl=PT30M");
	}
}
