package com.poco7250.moyeo.global.common;

import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.error.ErrorResponse;
import java.util.List;

/**
 * 모든 REST API가 공통으로 쓰는 응답 형식 { success, data, error }.
 * 성공이면 error가 null, 실패면 data가 null이다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

	/**
	 * 데이터를 담은 성공 응답을 만든다.
	 */
	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	/**
	 * 돌려줄 데이터가 없는 성공 응답을 만든다.
	 */
	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null);
	}

	/**
	 * 에러 코드로 실패 응답을 만든다.
	 */
	public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
		return new ApiResponse<>(false, null, ErrorResponse.of(errorCode));
	}

	/**
	 * 에러 코드와 필드 에러 목록으로 실패 응답을 만든다.
	 */
	public static <T> ApiResponse<T> fail(ErrorCode errorCode, List<ErrorResponse.FieldError> fieldErrors) {
		return new ApiResponse<>(false, null, ErrorResponse.of(errorCode, fieldErrors));
	}
}
