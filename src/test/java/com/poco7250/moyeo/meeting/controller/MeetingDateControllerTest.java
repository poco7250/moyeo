package com.poco7250.moyeo.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.MeetingDateStatus;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.DateCandidateVotes;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.service.MeetingDateService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MeetingDateController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = MeetingDateController.class)
@Import(SecurityTestConfig.class)
class MeetingDateControllerTest {

	private static final LocalDateTime FIRST = LocalDateTime.of(2099, 12, 20, 18, 0);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	MeetingDateService meetingDateService;

	@Test
	@DisplayName("후보를 추가하면 201과 후보별 가능·불가·미응답 현황을 준다")
	void addCandidate() throws Exception {
		given(meetingDateService.addCandidate(10L, 7L, FIRST)).willReturn(votes());

		mockMvc.perform(post("/api/meetings/10/date-candidates").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"startAt\":\"2099-12-20T18:00:00\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.dateStatus").value("VOTING"))
				.andExpect(jsonPath("$.data.startAt").doesNotExist())
				.andExpect(jsonPath("$.data.candidates[0].candidateId").value(100))
				.andExpect(jsonPath("$.data.candidates[0].startAt").value("2099-12-20T18:00:00"))
				.andExpect(jsonPath("$.data.candidates[0].availableCount").value(1))
				.andExpect(jsonPath("$.data.candidates[0].available[0].nickname").value("민수"))
				.andExpect(jsonPath("$.data.candidates[0].unavailable[0].nickname").value("영희"))
				.andExpect(jsonPath("$.data.candidates[0].unanswered[0].nickname").value("철수"));
	}

	@Test
	@DisplayName("내 응답은 가능 후보 목록을 그대로 서비스에 넘긴다(빈 목록 허용)")
	void respond() throws Exception {
		given(meetingDateService.respond(10L, 8L, List.of())).willReturn(votes());

		mockMvc.perform(put("/api/meetings/10/date-votes/me").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"availableCandidateIds\":[]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	@DisplayName("현황 조회는 200과 현황을 준다")
	void votesQuery() throws Exception {
		given(meetingDateService.getVotes(10L, 8L)).willReturn(votes());

		mockMvc.perform(get("/api/meetings/10/date-votes").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetingId").value(10));
	}

	@Test
	@DisplayName("확정하면 시작 시각이 채워진 모임 상세를 준다")
	void confirm() throws Exception {
		given(meetingDateService.confirm(10L, 7L, 100L)).willReturn(new MeetingDetailResponse(10L, 1L, "연말 모임",
				FIRST, MeetingDateStatus.CONFIRMED, null, null, 7L, true, "/share/meetings/t", "문구", List.of()));

		mockMvc.perform(post("/api/meetings/10/date/confirm").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"candidateId\":100}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.dateStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.startAt").value("2099-12-20T18:00:00"));
	}

	@Test
	@DisplayName("확정된 모임에 쓰면 409 MEETING_DATE_NOT_VOTING을 그대로 준다")
	void notVoting() throws Exception {
		given(meetingDateService.respond(eq(10L), eq(8L), any()))
				.willThrow(new BusinessException(ErrorCode.MEETING_DATE_NOT_VOTING));

		mockMvc.perform(put("/api/meetings/10/date-votes/me").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"availableCandidateIds\":[100]}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETING_DATE_NOT_VOTING"));
	}

	@ParameterizedTest(name = "{3}")
	@CsvSource(delimiter = '|', value = {
			"POST|/api/meetings/10/date-candidates|{}|후보 시각 없음",
			"POST|/api/meetings/10/date-candidates|{\"startAt\":\"2000-01-01T18:00:00\"}|지난 시각 후보",
			"PUT|/api/meetings/10/date-votes/me|{}|가능 후보 목록 없음",
			"PUT|/api/meetings/10/date-votes/me|{\"availableCandidateIds\":[null]}|목록에 null",
			"POST|/api/meetings/10/date/confirm|{}|확정할 후보 없음"})
	@DisplayName("요청 본문이 규칙에 맞지 않으면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
	void invalidBody(String method, String uri, String body, String caseName) throws Exception {
		mockMvc.perform((method.equals("PUT") ? put(uri) : post(uri)).cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(meetingDateService);
	}

	@Test
	@DisplayName("쿠키 없이 부르면 401이다")
	void unauthenticated() throws Exception {
		mockMvc.perform(get("/api/meetings/10/date-votes"))
				.andExpect(status().isUnauthorized());
	}

	private static MeetingDateVotesResponse votes() {
		return new MeetingDateVotesResponse(10L, MeetingDateStatus.VOTING, null, null, List.of(
				DateCandidateVotes.of(100L, FIRST, List.of(new BoardMember(8L, "민수", null)),
						List.of(new BoardMember(9L, "영희", null)), List.of(new BoardMember(7L, "철수", null)))));
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
