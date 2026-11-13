package com.poco7250.moyeo.global.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	// 에러 응답에 함께 실을 상세 데이터(예: 확정을 막은 벌칙 목록). 없으면 null
	private final transient Object details;

	/**
	 * 에러 코드에 정의된 기본 메시지로 예외를 만든다.
	 */
	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	/**
	 * 에러 응답에 상세 데이터를 함께 싣는 예외를 만든다.
	 *
	 * @param details 응답 error.details에 그대로 직렬화할 값
	 */
	public BusinessException(ErrorCode errorCode, Object details) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.details = details;
	}
}
