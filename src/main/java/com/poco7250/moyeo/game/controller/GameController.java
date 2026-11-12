package com.poco7250.moyeo.game.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.game.service.GameQueryService;
import com.poco7250.moyeo.game.service.GameRerunService;
import com.poco7250.moyeo.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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
 * 게임 방 API. 전부 로그인이 필요하고 게임이 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {

	private final GameLobbyService gameLobbyService;
	private final GameQueryService gameQueryService;
	private final GameRerunService gameRerunService;

	/**
	 * 차수의 게임 목록을 최근에 만든 순으로 조회한다. 실시간 알림을 놓친 멤버가 결과를 찾는 경로다.
	 */
	@GetMapping("/rounds/{roundId}/games")
	public ApiResponse<List<GameStateResponse>> getRoundGames(@LoginMember Long memberId, @PathVariable Long roundId) {
		return ApiResponse.ok(gameQueryService.getRoundGames(roundId, memberId));
	}

	/**
	 * 게임의 현재 상태를 조회한다. 재접속한 화면이 이걸로 상태를 복구한다.
	 */
	@GetMapping("/games/{gameId}/state")
	public ApiResponse<GameStateResponse> getState(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(gameQueryService.getState(gameId, memberId));
	}

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

	/**
	 * 공개된 게임을 다시 돌릴 새 회차를 만든다. 설정과 참가자는 원 회차를 복제하므로 요청 본문이 없다.
	 */
	@PostMapping("/games/{gameId}/rerun")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<GameStateResponse> rerun(@LoginMember Long memberId, @PathVariable Long gameId) {
		return ApiResponse.ok(gameRerunService.request(gameId, memberId));
	}
}
