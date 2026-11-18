package com.poco7250.moyeo.history.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.history.dto.HallOfFameResponse;
import com.poco7250.moyeo.history.service.HallOfFameService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 명예의 전당 API(PRD H-2). 그룹 멤버만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/hall-of-fame")
@RequiredArgsConstructor
public class HallOfFameController {

	static final int MIN_YEAR = 2000;
	static final int MAX_YEAR = 2999;

	private final HallOfFameService hallOfFameService;

	/**
	 * 그룹의 벌칙 순위·보드게임 꼴등 순위·출석부를 돌려준다. year가 없으면 전체 기간이다.
	 */
	@GetMapping
	public ApiResponse<HallOfFameResponse> get(@LoginMember Long memberId, @PathVariable Long groupId,
			@RequestParam(required = false) @Min(MIN_YEAR) @Max(MAX_YEAR) Integer year) {
		return ApiResponse.ok(hallOfFameService.get(groupId, memberId, year));
	}
}
