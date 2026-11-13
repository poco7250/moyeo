package com.poco7250.moyeo.penalty.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleRequest;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게임 후 벌칙 조건 API. 전부 로그인이 필요하고 모임 호스트만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/games/{gameId}")
@RequiredArgsConstructor
public class PenaltyController {

	private final PenaltyConditionService penaltyConditionService;

	/**
	 * 게임의 벌칙 금액 조건을 설정하거나 바꾼다.
	 */
	@PutMapping("/penalty-rule")
	public ApiResponse<PenaltyConditionResponse> setRule(@LoginMember Long memberId, @PathVariable Long gameId,
			@Valid @RequestBody PenaltyRuleRequest request) {
		return ApiResponse.ok(penaltyConditionService.setRule(gameId, memberId, request));
	}

	/**
	 * 게임의 벌칙 금액 조건을 해제한다.
	 */
	@DeleteMapping("/penalty-rule")
	public ApiResponse<PenaltyConditionResponse> clearRule(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(penaltyConditionService.clearRule(gameId, memberId));
	}

	/**
	 * 게임의 벌칙을 "금액 벌칙 없음"으로 면제한다.
	 */
	@PostMapping("/penalty-waiver")
	public ApiResponse<PenaltyConditionResponse> waive(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(penaltyConditionService.waive(gameId, memberId));
	}

	/**
	 * 게임의 벌칙 면제를 취소한다.
	 */
	@DeleteMapping("/penalty-waiver")
	public ApiResponse<PenaltyConditionResponse> unwaive(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(penaltyConditionService.unwaive(gameId, memberId));
	}
}
