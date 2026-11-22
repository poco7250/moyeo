package com.poco7250.moyeo.meeting.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.meeting.dto.MeetingGuestCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingGuestResponse;
import com.poco7250.moyeo.meeting.service.MeetingGuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 게스트 추가·삭제 API(PRD G-6). 호스트 권한자(호스트·방장)만 쓸 수 있고, 목록은 모임 상세의 guests로 본다.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/guests")
@RequiredArgsConstructor
public class MeetingGuestController {

	private final MeetingGuestService meetingGuestService;

	/**
	 * 이름만 있는 게스트를 모임에 추가한다.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MeetingGuestResponse> add(@LoginMember Long memberId, @PathVariable Long meetingId,
			@Valid @RequestBody MeetingGuestCreateRequest request) {
		return ApiResponse.ok(meetingGuestService.add(meetingId, memberId, request.name()));
	}

	/**
	 * 게임에 참가한 적 없는 게스트를 삭제한다.
	 */
	@DeleteMapping("/{guestId}")
	public ApiResponse<Void> remove(@LoginMember Long memberId, @PathVariable Long meetingId,
			@PathVariable Long guestId) {
		meetingGuestService.remove(meetingId, memberId, guestId);
		return ApiResponse.ok();
	}
}
