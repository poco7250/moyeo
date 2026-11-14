package com.poco7250.moyeo.meeting.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.meeting.dto.DateCandidateRequest;
import com.poco7250.moyeo.meeting.dto.DateConfirmRequest;
import com.poco7250.moyeo.meeting.dto.DateVoteRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.service.MeetingDateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날짜 미정 모임의 날짜 후보 투표 API(PRD M-5). 후보 추가·확정은 호스트만, 응답·조회는 그룹 멤버 누구나.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}")
@RequiredArgsConstructor
public class MeetingDateController {

	private final MeetingDateService meetingDateService;

	/**
	 * 날짜 후보를 추가하고 바뀐 투표 현황을 돌려준다(호스트).
	 */
	@PostMapping("/date-candidates")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MeetingDateVotesResponse> addCandidate(@LoginMember Long memberId,
			@PathVariable Long meetingId, @Valid @RequestBody DateCandidateRequest request) {
		return ApiResponse.ok(meetingDateService.addCandidate(meetingId, memberId, request.startAt()));
	}

	/**
	 * 내 가능 후보를 설정한다. 보내지 않은 후보는 불가로 저장한다.
	 */
	@PutMapping("/date-votes/me")
	public ApiResponse<MeetingDateVotesResponse> respond(@LoginMember Long memberId, @PathVariable Long meetingId,
			@Valid @RequestBody DateVoteRequest request) {
		return ApiResponse.ok(meetingDateService.respond(meetingId, memberId, request.availableCandidateIds()));
	}

	/**
	 * 후보별 가능·불가·미응답 현황을 돌려준다.
	 */
	@GetMapping("/date-votes")
	public ApiResponse<MeetingDateVotesResponse> votes(@LoginMember Long memberId, @PathVariable Long meetingId) {
		return ApiResponse.ok(meetingDateService.getVotes(meetingId, memberId));
	}

	/**
	 * 고른 후보로 날짜를 확정하고 모임 상세를 돌려준다(호스트).
	 */
	@PostMapping("/date/confirm")
	public ApiResponse<MeetingDetailResponse> confirm(@LoginMember Long memberId, @PathVariable Long meetingId,
			@Valid @RequestBody DateConfirmRequest request) {
		return ApiResponse.ok(meetingDateService.confirm(meetingId, memberId, request.candidateId()));
	}
}
