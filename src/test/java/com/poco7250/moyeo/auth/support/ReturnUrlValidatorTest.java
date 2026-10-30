package com.poco7250.moyeo.auth.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.config.AuthRedirectProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReturnUrlValidatorTest {

	private final ReturnUrlValidator validator = new ReturnUrlValidator(new AuthRedirectProperties("", "/"));

	@Test
	@DisplayName("같은 오리진 상대 경로는 쿼리스트링까지 그대로 유지한다")
	void keepsRelativePath() {
		assertThat(validator.sanitize("/invites/abc?x=1")).isEqualTo("/invites/abc?x=1");
	}

	@ParameterizedTest
	@ValueSource(strings = {"//evil.com", "https://evil.com", "/\\evil", "javascript:alert(1)",
			"/a\r\nSet-Cookie:x=1", "/a\nb", "invites/abc", "   "})
	@DisplayName("외부 주소·프로토콜 상대 URL·역슬래시·줄바꿈·공백은 기본 경로로 바꾼다")
	void rejectsUnsafe(String raw) {
		assertThat(validator.sanitize(raw)).isEqualTo("/");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@DisplayName("null이나 빈 값은 기본 경로로 바꾼다")
	void rejectsNullOrEmpty(String raw) {
		assertThat(validator.sanitize(raw)).isEqualTo("/");
	}

	@Test
	@DisplayName("2048자까지는 허용하고 2049자부터는 기본 경로로 바꾼다")
	void limitsLength() {
		String max = "/" + "a".repeat(2047);
		String tooLong = "/" + "a".repeat(2048);

		assertThat(validator.sanitize(max)).isEqualTo(max);
		assertThat(validator.sanitize(tooLong)).isEqualTo("/");
	}

	@Test
	@DisplayName("기본 경로 설정을 바꾸면 그 경로로 보낸다")
	void usesConfiguredDefaultPath() {
		ReturnUrlValidator custom = new ReturnUrlValidator(new AuthRedirectProperties("", "/home"));

		assertThat(custom.sanitize("//evil.com")).isEqualTo("/home");
	}
}
