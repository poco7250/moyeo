package com.poco7250.moyeo.place.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import com.poco7250.moyeo.place.service.PlaceVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 후보 투표 API. 전부 로그인이 필요하고 차수가 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/rounds/{roundId}")
@RequiredArgsConstructor
public class PlaceVoteController {

	private final PlaceVoteService placeVoteService;

	/**
	 * 후보에 투표한다. 같은 후보에 다시 보내도 표는 하나다.
	 */
	@PutMapping("/candidates/{candidateId}/vote")
	public ApiResponse<RoundVotesResponse> vote(@LoginMember Long memberId, @PathVariable Long roundId,
			@PathVariable Long candidateId) {
		return ApiResponse.ok(placeVoteService.vote(roundId, candidateId, memberId));
	}

	/**
	 * 내 표를 취소한다. 표가 없어도 성공한다.
	 */
	@DeleteMapping("/candidates/{candidateId}/vote")
	public ApiResponse<RoundVotesResponse> cancelVote(@LoginMember Long memberId, @PathVariable Long roundId,
			@PathVariable Long candidateId) {
		return ApiResponse.ok(placeVoteService.cancel(roundId, candidateId, memberId));
	}

	/**
	 * 차수의 후보별 득표 수와 투표자 목록을 조회한다.
	 */
	@GetMapping("/votes")
	public ApiResponse<RoundVotesResponse> getVotes(@LoginMember Long memberId, @PathVariable Long roundId) {
		return ApiResponse.ok(placeVoteService.getVotes(roundId, memberId));
	}
}
