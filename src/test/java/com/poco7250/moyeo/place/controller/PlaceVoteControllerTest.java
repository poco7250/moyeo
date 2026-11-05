package com.poco7250.moyeo.place.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.place.dto.CandidateVotes;
import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import com.poco7250.moyeo.place.service.PlaceVoteService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PlaceVoteController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = PlaceVoteController.class)
@Import(SecurityTestConfig.class)
class PlaceVoteControllerTest {

	private static final String VOTE_URL = "/api/rounds/100/candidates/500/vote";
	private static final String VOTES_URL = "/api/rounds/100/votes";
	private static final RoundVotesResponse RESPONSE = new RoundVotesResponse(100L, List.of(
			new CandidateVotes(500L, 1, List.of(new BoardMember(8L, "멤버", "🍖"))),
			new CandidateVotes(501L, 0, List.of())));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	PlaceVoteService placeVoteService;

	@Test
	@DisplayName("투표하면 200과 후보별 득표 수·투표자를 준다")
	void vote() throws Exception {
		given(placeVoteService.vote(100L, 500L, 8L)).willReturn(RESPONSE);

		mockMvc.perform(put(VOTE_URL).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.roundId").value(100))
				.andExpect(jsonPath("$.data.candidates[0].candidateId").value(500))
				.andExpect(jsonPath("$.data.candidates[0].voteCount").value(1))
				.andExpect(jsonPath("$.data.candidates[0].voters[0].memberId").value(8))
				.andExpect(jsonPath("$.data.candidates[0].voters[0].nickname").value("멤버"))
				.andExpect(jsonPath("$.data.candidates[0].voters[0].emoji").value("🍖"))
				.andExpect(jsonPath("$.data.candidates[1].voteCount").value(0));
	}

	@Test
	@DisplayName("표를 취소하면 200과 현황을 준다")
	void cancelVote() throws Exception {
		given(placeVoteService.cancel(100L, 500L, 8L)).willReturn(RESPONSE);

		mockMvc.perform(delete(VOTE_URL).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roundId").value(100));
	}

	@Test
	@DisplayName("득표 현황을 조회한다")
	void getVotes() throws Exception {
		given(placeVoteService.getVotes(100L, 8L)).willReturn(RESPONSE);

		mockMvc.perform(get(VOTES_URL).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.candidates.length()").value(2));
	}

	@Test
	@DisplayName("비로그인은 401이고 서비스를 호출하지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(put(VOTE_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(VOTE_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(VOTES_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(placeVoteService);
	}

	@Test
	@DisplayName("비멤버는 403 NOT_GROUP_MEMBER다")
	void nonMember() throws Exception {
		given(placeVoteService.getVotes(100L, 8L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get(VOTES_URL).cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("이 차수의 후보가 아니면 404 PLACE_CANDIDATE_NOT_FOUND다")
	void candidateNotFound() throws Exception {
		given(placeVoteService.vote(100L, 500L, 8L))
				.willThrow(new BusinessException(ErrorCode.PLACE_CANDIDATE_NOT_FOUND));

		mockMvc.perform(put(VOTE_URL).cookie(accessCookie(8L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_CANDIDATE_NOT_FOUND"));
	}

	@Test
	@DisplayName("마감 후 투표는 409 PLACE_VOTE_DEADLINE_PASSED다")
	void deadlinePassed() throws Exception {
		given(placeVoteService.vote(100L, 500L, 8L))
				.willThrow(new BusinessException(ErrorCode.PLACE_VOTE_DEADLINE_PASSED));

		mockMvc.perform(put(VOTE_URL).cookie(accessCookie(8L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PLACE_VOTE_DEADLINE_PASSED"));
	}

	@Test
	@DisplayName("투표 중이 아닌 차수에 투표하면 409 ROUND_PLACE_INVALID_TRANSITION이다")
	void notVoting() throws Exception {
		given(placeVoteService.vote(100L, 500L, 8L))
				.willThrow(new BusinessException(ErrorCode.ROUND_PLACE_INVALID_TRANSITION));

		mockMvc.perform(put(VOTE_URL).cookie(accessCookie(8L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ROUND_PLACE_INVALID_TRANSITION"));
	}

	@Test
	@DisplayName("숫자가 아닌 id는 400 INVALID_INPUT이다")
	void invalidId() throws Exception {
		mockMvc.perform(put("/api/rounds/100/candidates/abc/vote").cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		verifyNoInteractions(placeVoteService);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
