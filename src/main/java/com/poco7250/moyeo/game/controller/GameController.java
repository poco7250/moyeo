package com.poco7250.moyeo.game.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게임 방 API. 전부 로그인이 필요하고 게임이 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {

	private final GameLobbyService gameLobbyService;

	/**
	 * 차수 참석자 중 고른 회원으로 게임 방을 만든다.
	 */
	@PostMapping("/rounds/{roundId}/games")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<GameStateResponse> create(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody GameCreateRequest request) {
		return ApiResponse.ok(gameLobbyService.create(roundId, memberId, request));
	}

	/**
	 * 게임을 시작해 벌칙자를 뽑고 공개한다.
	 */
	@PostMapping("/games/{gameId}/start")
	public ApiResponse<GameStateResponse> start(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(gameLobbyService.start(gameId, memberId));
	}

	/**
	 * 공개된 게임을 종료한다.
	 */
	@PostMapping("/games/{gameId}/close")
	public ApiResponse<GameStateResponse> close(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(gameLobbyService.close(gameId, memberId));
	}

	/**
	 * 공개 전 게임을 취소한다.
	 */
	@PostMapping("/games/{gameId}/cancel")
	public ApiResponse<GameStateResponse> cancel(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(gameLobbyService.cancel(gameId, memberId));
	}
}
