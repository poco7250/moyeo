package com.poco7250.moyeo.settlement.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.settlement.dto.ChangeLogPageResponse;
import com.poco7250.moyeo.settlement.service.ChangeLogQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 변경 로그 조회 API. 그룹 멤버만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/change-logs")
@RequiredArgsConstructor
public class ChangeLogController {

	private static final int MAX_SIZE = 100;

	private final ChangeLogQueryService changeLogQueryService;

	/**
	 * 변경 로그를 오래된 순으로 한 페이지씩 돌려준다.
	 */
	@GetMapping
	public ApiResponse<ChangeLogPageResponse> list(@LoginMember Long memberId, @PathVariable Long meetingId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(MAX_SIZE) int size) {
		return ApiResponse.ok(changeLogQueryService.list(meetingId, memberId, page, size));
	}
}
