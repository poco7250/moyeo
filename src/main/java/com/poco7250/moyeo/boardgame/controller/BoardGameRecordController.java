package com.poco7250.moyeo.boardgame.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordResponse;
import com.poco7250.moyeo.boardgame.service.BoardGameRecordService;
import com.poco7250.moyeo.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보드게임 승패 기록 API(PRD R-7). 전부 로그인이 필요하고 차수가 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardGameRecordController {

	private final BoardGameRecordService boardGameRecordService;

	/**
	 * 차수의 보드게임 기록을 최근에 만든 순으로 조회한다.
	 */
	@GetMapping("/rounds/{roundId}/board-games")
	public ApiResponse<List<BoardGameRecordResponse>> list(@LoginMember Long memberId, @PathVariable Long roundId) {
		return ApiResponse.ok(boardGameRecordService.list(roundId, memberId));
	}

	/**
	 * 차수 참석자 중 고른 회원의 순위로 보드게임 기록을 만든다.
	 */
	@PostMapping("/rounds/{roundId}/board-games")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<BoardGameRecordResponse> create(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody BoardGameRecordRequest request) {
		return ApiResponse.ok(boardGameRecordService.create(roundId, memberId, request));
	}

	/**
	 * 보드게임 기록의 게임명과 순위를 통째로 바꾼다. 기록한 사람이나 모임 호스트만 할 수 있다.
	 */
	@PutMapping("/board-games/{recordId}")
	public ApiResponse<BoardGameRecordResponse> update(@LoginMember Long memberId, @PathVariable Long recordId,
			@Valid @RequestBody BoardGameRecordRequest request) {
		return ApiResponse.ok(boardGameRecordService.update(recordId, memberId, request));
	}

	/**
	 * 보드게임 기록을 지운다. 기록한 사람이나 모임 호스트만 할 수 있다.
	 */
	@DeleteMapping("/board-games/{recordId}")
	public ApiResponse<Void> delete(@LoginMember Long memberId, @PathVariable Long recordId) {
		boardGameRecordService.delete(recordId, memberId);
		return ApiResponse.ok();
	}
}
