package com.poco7250.moyeo.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RandomTokensTest {

	@Test
	void 토큰은_URL_안전_문자_43자이고_매번_다르다() {
		String first = RandomTokens.urlSafe();
		String second = RandomTokens.urlSafe();

		assertThat(first).hasSize(RandomTokens.LENGTH).matches("[A-Za-z0-9_-]+");
		assertThat(first).isNotEqualTo(second);
	}
}
