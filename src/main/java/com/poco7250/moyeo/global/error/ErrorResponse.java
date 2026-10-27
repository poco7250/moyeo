package com.poco7250.moyeo.global.error;

import java.util.List;

/**
 * API 에러 응답 본문. 입력값 검증 실패 시에는 필드별 에러 목록을 함께 담는다.
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

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
		return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.copyOf(fieldErrors));
	}

	/**
	 * 검증에 실패한 필드와 그 이유.
	 */
	public record FieldError(String field, String reason) {
	}
}
