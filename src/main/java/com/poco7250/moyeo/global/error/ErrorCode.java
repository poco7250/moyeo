package com.poco7250.moyeo.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 요청과 충돌했습니다. 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// 인증
	KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "카카오 로그인에 실패했습니다. 다시 시도해 주세요."),
	KAKAO_API_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다. 다시 로그인해 주세요."),
	EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.");

	private final HttpStatus status;
	private final String message;
}
