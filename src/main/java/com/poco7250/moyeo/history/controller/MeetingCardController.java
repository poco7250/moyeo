package com.poco7250.moyeo.history.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.history.dto.MeetingCardResponse;
import com.poco7250.moyeo.history.service.MeetingCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 결산 카드 API(PRD H-3). 그룹 멤버만 볼 수 있다.
 */
@RestController
@RequiredArgsConstructor
public class MeetingCardController {

	private final MeetingCardService meetingCardService;

	/**
	 * 지난 모임의 결산 카드 데이터를 돌려준다.
	 */
	@GetMapping("/api/meetings/{meetingId}/summary-card")
	public ApiResponse<MeetingCardResponse> get(@LoginMember Long memberId, @PathVariable Long meetingId) {
		return ApiResponse.ok(meetingCardService.get(meetingId, memberId));
	}
}
