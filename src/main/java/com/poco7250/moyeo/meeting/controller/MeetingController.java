package com.poco7250.moyeo.meeting.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.meeting.dto.GroupMeetingsResponse;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 API. 전부 로그인이 필요하고, 그룹 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeetingController {

	private final MeetingService meetingService;

	/**
	 * 그룹에 모임을 만들고 모임 상세(공유 링크 포함)를 돌려준다.
	 */
	@PostMapping("/groups/{groupId}/meetings")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MeetingDetailResponse> create(@LoginMember Long memberId, @PathVariable Long groupId,
			@Valid @RequestBody MeetingCreateRequest request) {
		return ApiResponse.ok(meetingService.create(groupId, memberId, request));
	}

	/**
	 * 그룹의 다가오는 모임·지난 모임 목록을 돌려준다.
	 */
	@GetMapping("/groups/{groupId}/meetings")
	public ApiResponse<GroupMeetingsResponse> groupMeetings(@LoginMember Long memberId, @PathVariable Long groupId) {
		return ApiResponse.ok(meetingService.getGroupMeetings(groupId, memberId));
	}

	/**
	 * 모임 상세(차수·장소 상태·호스트 여부)를 돌려준다.
	 */
	@GetMapping("/meetings/{meetingId}")
	public ApiResponse<MeetingDetailResponse> detail(@LoginMember Long memberId, @PathVariable Long meetingId) {
		return ApiResponse.ok(meetingService.getDetail(meetingId, memberId));
	}
}
