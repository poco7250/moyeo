package com.poco7250.moyeo.global.error;

import java.util.List;

/**
 * API 에러 응답 본문. 입력값 검증 실패 시에는 필드별 에러 목록을, 비즈니스 에러에 따라서는 상세 데이터를 함께 담는다.
 *
 * @param details 에러별 상세 데이터(예: 확정을 막은 벌칙 목록). 없으면 null
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors, Object details) {

	/**
	 * 필드 에러 없이 에러 코드만으로 응답을 만든다.
	 */
	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, List.of());
	}

	/**
	 * 에러 코드와 필드 에러 목록으로 응답을 만든다.
	 */
	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
		return of(errorCode, fieldErrors, null);
	}

	/**
	 * 에러 코드·필드 에러 목록·상세 데이터로 응답을 만든다.
	 */
	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors, Object details) {
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.copyOf(fieldErrors), details);
	}

	/**
	 * 검증에 실패한 필드와 그 이유.
	 */
	public record FieldError(String field, String reason) {
	}
}
