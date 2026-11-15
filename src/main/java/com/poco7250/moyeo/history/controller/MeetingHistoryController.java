package com.poco7250.moyeo.history.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryPageResponse;
import com.poco7250.moyeo.history.service.MeetingHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 히스토리 API(PRD H-1). 그룹 멤버만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/history")
@RequiredArgsConstructor
public class MeetingHistoryController {

	static final int MAX_SIZE = 50;

	private final MeetingHistoryService meetingHistoryService;

	/**
	 * 그룹의 지난 모임 히스토리를 최근 순으로 한 페이지씩 돌려준다.
	 */
	@GetMapping
	public ApiResponse<MeetingHistoryPageResponse> list(@LoginMember Long memberId, @PathVariable Long groupId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_SIZE) int size) {
		return ApiResponse.ok(meetingHistoryService.list(groupId, memberId, page, size));
	}
}
