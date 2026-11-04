package com.poco7250.moyeo.place.validation;

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
 * https 스킴이고 2,048자 이하인 링크만 허용한다. null은 통과(선택 입력)한다.
 * http·javascript 같은 다른 스킴은 모두 거부한다.
 */
@Documented
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = HttpsUrlValidator.class)
public @interface HttpsUrl {

	String message() default "https로 시작하는 2,048자 이하 링크만 입력할 수 있습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
