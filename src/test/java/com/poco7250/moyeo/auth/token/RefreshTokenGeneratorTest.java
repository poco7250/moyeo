package com.poco7250.moyeo.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorTest {

	private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

	@Test
	@DisplayName("원문은 매번 다르고 URL-safe Base64(패딩 없음) 43자다")
	void generate() {
		String first = generator.generate();
		String second = generator.generate();

		assertThat(first).isNotEqualTo(second).hasSize(43).matches("[A-Za-z0-9_-]+");
	}

	@Test
	@DisplayName("해시는 원문과 다른 소문자 hex 64자이고 같은 입력이면 같은 값이다")
	void hash() {
		String raw = generator.generate();

		String hash = generator.hash(raw);

		assertThat(hash).hasSize(64).matches("[0-9a-f]+").isNotEqualTo(raw);
		assertThat(generator.hash(raw)).isEqualTo(hash);
	}
}
