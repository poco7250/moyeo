package com.poco7250.moyeo.global.push;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 알려진 브라우저 푸시 서비스의 https 주소(2,048자 이하)만 허용한다. null은 통과(필수 여부는 @NotBlank 몫).
 * 서버가 이 주소로 요청을 보내므로 임의 호스트를 막아 서버 측 요청 위조(SSRF)를 막는다.
 */
@Documented
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = PushEndpointValidator.class)
public @interface PushEndpoint {

	/** 허용하는 최대 길이. 저장 컬럼 길이도 이 값을 쓴다 */
	int MAX_LENGTH = 2048;

	String message() default "지원하는 브라우저 푸시 서비스 주소가 아닙니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
