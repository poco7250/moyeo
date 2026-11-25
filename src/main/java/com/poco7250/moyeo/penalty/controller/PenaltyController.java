package com.poco7250.moyeo.penalty.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 금액 벌칙 면제 API. 전부 로그인이 필요하고 모임 정산 권한(호스트·방장·총무)만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/games/{gameId}/penalty-waiver")
@RequiredArgsConstructor
public class PenaltyController {

	private final PenaltyConditionService penaltyConditionService;

	/**
	 * 게임의 금액 벌칙을 면제한다.
	 */
	@PostMapping
	public ApiResponse<PenaltyConditionResponse> waive(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(penaltyConditionService.waive(gameId, memberId));
	}

	/**
	 * 게임의 벌칙 면제를 취소한다.
	 */
	@DeleteMapping
	public ApiResponse<PenaltyConditionResponse> unwaive(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(penaltyConditionService.unwaive(gameId, memberId));
	}
}
