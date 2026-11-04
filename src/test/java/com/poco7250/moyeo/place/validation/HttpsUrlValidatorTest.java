package com.poco7250.moyeo.place.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpsUrlValidatorTest {

	private static final String HTTPS_PREFIX = "https://example.com/";

	private static ValidatorFactory factory;
	private static Validator validator;

	// 검증 대상(요청 DTO 대용)
	record Target(@HttpsUrl String url) {
	}

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private boolean isValid(String url) {
		return validator.validate(new Target(url)).isEmpty();
	}

	/** 지정한 전체 길이의 https 링크를 만든다. */
	private String httpsUrlOfLength(int length) {
		return HTTPS_PREFIX + "a".repeat(length - HTTPS_PREFIX.length());
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://map.kakao.com/link/search/강남", "https://place.map.kakao.com/123", "HTTPS://example.com"})
	@DisplayName("https 링크는 통과한다")
	void httpsPasses(String url) {
		assertThat(isValid(url)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"http://example.com", "javascript:alert(1)", "ftp://example.com/a", "", " ", "https:///path", "example.com", "https://exa mple.com"})
	@DisplayName("https가 아니거나 호스트가 없거나 형식이 깨진 값은 실패한다")
	void nonHttpsFails(String url) {
		assertThat(isValid(url)).isFalse();
	}

	@Test
	@DisplayName("2,048자는 통과하고 2,049자는 실패한다")
	void maxLength() {
		assertThat(isValid(httpsUrlOfLength(2048))).isTrue();
		assertThat(isValid(httpsUrlOfLength(2049))).isFalse();
	}

	@Test
	@DisplayName("null은 선택 입력이라 통과한다")
	void nullPasses() {
		assertThat(isValid(null)).isTrue();
	}
}
