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
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보가 만료되었습니다. 다시 로그인해 주세요."),
	INVALID_OAUTH_STATE(HttpStatus.UNAUTHORIZED, "로그인 요청이 유효하지 않습니다. 다시 시도해 주세요."),

	// 실시간(WebSocket)
	// 해석할 수 없는 토픽 구독, 클라이언트의 /topic 직접 발행
	REALTIME_DESTINATION_FORBIDDEN(HttpStatus.FORBIDDEN, "허용되지 않은 실시간 경로입니다."),

	// 회원
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),

	// 그룹
	NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "그룹 멤버만 이용할 수 있습니다."),
	GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."),

	// 모임
	NOT_MEETING_HOST(HttpStatus.FORBIDDEN, "모임 호스트만 할 수 있습니다."),
	MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."),
	ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "차수를 찾을 수 없습니다."),

	// 참석
	ATTENDANCE_DEADLINE_PASSED(HttpStatus.FORBIDDEN, "응답 마감 시간이 지났습니다. 호스트에게 수정을 요청해 주세요."),
	// 늦참·1차만 같은 단축 옵션은 차수가 2개 이상일 때만 의미가 있다
	ATTENDANCE_SHORTCUT_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "차수가 2개 이상인 모임에서만 쓸 수 있는 옵션입니다."),
	// 호스트 대리 수정 대상이 이 그룹 멤버가 아님
	ATTENDANCE_TARGET_NOT_MEMBER(HttpStatus.NOT_FOUND, "응답을 수정할 멤버를 찾을 수 없습니다."),

	// 장소
	// 카카오 로컬 키 오류·쿼터 초과·장애·타임아웃. 프론트는 이 코드면 직접 입력(이름 + 주소·지도 링크) 폼을 보여준다
	PLACE_SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 장소 검색을 쓸 수 없습니다. 장소 이름과 주소를 직접 입력해 주세요."),
	PLACE_SEARCH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "검색 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

	// 초대
	INVITE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다."),
	// 만료·폐기된 링크. 예전엔 있었지만 이제 못 쓴다는 의미라 410으로 404와 구분한다
	INVITE_LINK_EXPIRED(HttpStatus.GONE, "만료된 초대 링크입니다. 새 링크를 받아 주세요.");

	private final HttpStatus status;
	private final String message;
}
