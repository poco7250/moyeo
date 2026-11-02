package com.poco7250.moyeo.meeting.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceBoardResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import com.poco7250.moyeo.meeting.service.AttendanceBoardService;
import com.poco7250.moyeo.meeting.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참석 응답·현황판 API. 전부 로그인이 필요하고, 모임이 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}")
@RequiredArgsConstructor
public class AttendanceController {

	private final AttendanceService attendanceService;
	private final AttendanceBoardService attendanceBoardService;

	/**
	 * 내 차수별 참석 응답을 저장한다(단축 옵션 또는 차수별 응답).
	 */
	@PutMapping("/attendances/me")
	public ApiResponse<MemberAttendanceResponse> respondMine(@LoginMember Long memberId,
			@PathVariable Long meetingId, @Valid @RequestBody AttendanceRespondRequest request) {
		return ApiResponse.ok(attendanceService.respondMine(meetingId, memberId, request));
	}

	/**
	 * 호스트가 다른 멤버의 응답을 대신 저장한다(마감 후에도 가능).
	 */
	@PutMapping("/attendances/{targetMemberId}")
	public ApiResponse<MemberAttendanceResponse> respondFor(@LoginMember Long memberId,
			@PathVariable Long meetingId, @PathVariable Long targetMemberId,
			@Valid @RequestBody AttendanceRespondRequest request) {
		return ApiResponse.ok(attendanceService.respondFor(meetingId, memberId, targetMemberId, request));
	}

	/**
	 * 차수별 참석 현황판을 돌려준다.
	 */
	@GetMapping("/attendance-board")
	public ApiResponse<AttendanceBoardResponse> board(@LoginMember Long memberId, @PathVariable Long meetingId) {
		return ApiResponse.ok(attendanceBoardService.getBoard(meetingId, memberId));
	}
}
