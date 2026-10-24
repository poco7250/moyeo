package com.poco7250.moyeo.global.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	/**
	 * 에러 코드에 정의된 기본 메시지로 예외를 만든다.
	 */
	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
