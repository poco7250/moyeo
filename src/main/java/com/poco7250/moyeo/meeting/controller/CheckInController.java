package com.poco7250.moyeo.meeting.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceConfirmResponse;
import com.poco7250.moyeo.meeting.dto.CheckInResponse;
import com.poco7250.moyeo.meeting.service.CheckInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당일 체크인·실제 참석자 확정 API(PRD M-7). 전부 로그인이 필요하고 모임 당일부터 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/rounds/{roundId}")
@RequiredArgsConstructor
public class CheckInController {

	private final CheckInService checkInService;

	/**
	 * 내가 도착했다고 체크인한다.
	 */
	@PutMapping("/check-ins/me")
	public ApiResponse<CheckInResponse> checkInMine(@LoginMember Long memberId, @PathVariable Long roundId) {
		return ApiResponse.ok(checkInService.changeCheckIn(roundId, memberId, memberId, true));
	}

	/**
	 * 내 체크인을 취소한다.
	 */
	@DeleteMapping("/check-ins/me")
	public ApiResponse<CheckInResponse> cancelMine(@LoginMember Long memberId, @PathVariable Long roundId) {
		return ApiResponse.ok(checkInService.changeCheckIn(roundId, memberId, memberId, false));
	}

	/**
	 * 호스트가 다른 멤버를 대신 체크인한다.
	 */
	@PutMapping("/check-ins/{targetMemberId}")
	public ApiResponse<CheckInResponse> checkInFor(@LoginMember Long memberId, @PathVariable Long roundId,
			@PathVariable Long targetMemberId) {
		return ApiResponse.ok(checkInService.changeCheckIn(roundId, memberId, targetMemberId, true));
	}

	/**
	 * 호스트가 다른 멤버의 체크인을 대신 취소한다.
	 */
	@DeleteMapping("/check-ins/{targetMemberId}")
	public ApiResponse<CheckInResponse> cancelFor(@LoginMember Long memberId, @PathVariable Long roundId,
			@PathVariable Long targetMemberId) {
		return ApiResponse.ok(checkInService.changeCheckIn(roundId, memberId, targetMemberId, false));
	}

	/**
	 * 호스트가 차수의 실제 참석자를 확정한다(체크인 = 참석, 나머지 = 불참).
	 */
	@PostMapping("/attendance/confirm")
	public ApiResponse<AttendanceConfirmResponse> confirm(@LoginMember Long memberId, @PathVariable Long roundId) {
		return ApiResponse.ok(checkInService.confirm(roundId, memberId));
	}
}
