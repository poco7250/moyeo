package com.poco7250.moyeo.group.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteTokenGeneratorTest {

	private final InviteTokenGenerator generator = new InviteTokenGenerator();

	@Test
	@DisplayName("토큰은 URL-safe 문자로 된 43자다")
	void urlSafe43Chars() {
		String token = generator.generate();

		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
	}

	@Test
	@DisplayName("생성할 때마다 다른 토큰이 나온다")
	void differentEachTime() {
		assertThat(generator.generate()).isNotEqualTo(generator.generate());
	}
}
