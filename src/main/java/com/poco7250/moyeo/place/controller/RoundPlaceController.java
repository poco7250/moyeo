package com.poco7250.moyeo.place.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.PlaceConfirmRequest;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.dto.VotingOpenRequest;
import com.poco7250.moyeo.place.service.PlaceCandidateService;
import com.poco7250.moyeo.place.service.RoundPlaceService;
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
 * 차수 장소 결정 API(후보 추가·바로 확정·수동 확정·투표 열기). 전부 로그인이 필요하고 차수가 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/rounds/{roundId}")
@RequiredArgsConstructor
public class RoundPlaceController {

	private final PlaceCandidateService placeCandidateService;
	private final RoundPlaceService roundPlaceService;

	/**
	 * 투표 중인 차수에 직접 입력한 장소 후보를 추가한다.
	 */
	@PostMapping("/candidates")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CandidateResponse> addCandidate(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody PlaceInput request) {
		return ApiResponse.ok(placeCandidateService.add(roundId, memberId, request));
	}

	/**
	 * 호스트가 투표 없이 장소를 바로 확정한다.
	 */
	@PostMapping("/place/confirm-direct")
	public ApiResponse<RoundPlaceResponse> confirmDirectly(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody PlaceInput request) {
		return ApiResponse.ok(roundPlaceService.confirmDirectly(roundId, memberId, request));
	}

	/**
	 * 호스트가 투표 중인 후보 하나를 골라 확정한다.
	 */
	@PostMapping("/place/confirm")
	public ApiResponse<RoundPlaceResponse> confirmManually(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody PlaceConfirmRequest request) {
		return ApiResponse.ok(roundPlaceService.confirmManually(roundId, memberId, request.candidateId()));
	}

	/**
	 * 호스트가 장소 투표를 연다(확정된 차수나 호스트 선택 대기 차수면 재투표, 표 초기화).
	 */
	@PostMapping("/place/voting")
	public ApiResponse<RoundPlaceResponse> openVoting(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody VotingOpenRequest request) {
		return ApiResponse.ok(roundPlaceService.openVoting(roundId, memberId, request.placeVoteDeadline()));
	}
}
