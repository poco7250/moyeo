package com.poco7250.moyeo.history.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.history.dto.YearReviewResponse;
import com.poco7250.moyeo.history.service.YearReviewService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연말 결산 API(PRD H-4). 그룹 멤버만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/year-review")
@RequiredArgsConstructor
public class YearReviewController {

	private final YearReviewService yearReviewService;

	/**
	 * 그해 모임 수·지출 합계·참석/부담 순위·많이 간 장소·벌칙왕과 공유 문구를 돌려준다. year는 필수다.
	 */
	@GetMapping
	public ApiResponse<YearReviewResponse> get(@LoginMember Long memberId, @PathVariable Long groupId,
			@RequestParam @Min(HallOfFameController.MIN_YEAR) @Max(HallOfFameController.MAX_YEAR) Integer year) {
		return ApiResponse.ok(yearReviewService.get(groupId, memberId, year));
	}
}
