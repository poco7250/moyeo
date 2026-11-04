package com.poco7250.moyeo.place.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceInputTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	@Test
	@DisplayName("앞뒤 공백을 지우고 비어 있는 선택 입력은 null로 바꾼다")
	void normalize() {
		PlaceInput input = new PlaceInput("  고깃집 ", "   ", "");

		assertThat(input.label()).isEqualTo("고깃집");
		assertThat(input.manualAddress()).isNull();
		assertThat(input.manualMapUrl()).isNull();
	}

	@Test
	@DisplayName("빈 지도 링크는 null이 되어 https 검증을 통과한다")
	void blankMapUrlPasses() {
		assertThat(validator.validate(new PlaceInput("고깃집", null, "  "))).isEmpty();
	}

	@Test
	@DisplayName("공백만 있는 이름은 비어 있는 이름으로 검증에 실패한다")
	void blankLabelFails() {
		assertThat(validator.validate(new PlaceInput("   ", null, null)))
				.extracting(v -> v.getPropertyPath().toString())
				.containsExactly("label");
	}

	@Test
	@DisplayName("사용자 입력으로 장소 값 타입을 만든다")
	void toPlace() {
		PlaceInput input = new PlaceInput("고깃집", " 강남대로 1 ", " https://place.map.kakao.com/1 ");

		assertThat(input.toPlace().getManualAddress()).isEqualTo("강남대로 1");
		assertThat(input.toPlace().getManualMapUrl()).isEqualTo("https://place.map.kakao.com/1");
	}
}
