package com.poco7250.moyeo.history.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.history.dto.CardShareResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.MeetingCardResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryResponse;
import com.poco7250.moyeo.history.service.MeetingCardService;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MeetingCardController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = MeetingCardController.class)
@Import(SecurityTestConfig.class)
class MeetingCardControllerTest {

	private static final String URL = "/api/meetings/10/summary-card";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	MeetingCardService meetingCardService;

	@Test
	@DisplayName("결산 카드 데이터를 공통 응답 래퍼로 준다")
	void response() throws Exception {
		HistoryMemberResponse minsu = new HistoryMemberResponse(3L, "민수", "🎳", false);
		given(meetingCardService.get(10L, 8L)).willReturn(new MeetingCardResponse("대학 동기",
				new MeetingHistoryResponse(10L, "11월 모임", LocalDateTime.of(2026, 11, 1, 18, 0), 3, 87_650L,
						SettlementStatus.CONFIRMED, 1, List.of(), List.of()),
				List.of(new CardShareResponse(minsu, 35_000L))));

		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.groupName").value("대학 동기"))
				.andExpect(jsonPath("$.data.meeting.title").value("11월 모임"))
				.andExpect(jsonPath("$.data.meeting.startAt").value("2026-11-01T18:00:00"))
				.andExpect(jsonPath("$.data.meeting.totalExpenseAmount").value(87_650))
				.andExpect(jsonPath("$.data.meeting.settlementStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.shares[0].member.nickname").value("민수"))
				.andExpect(jsonPath("$.data.shares[0].shareAmount").value(35_000));
	}

	@Test
	@DisplayName("지난 모임이 아니면 409 MEETING_NOT_PAST")
	void rejectsNotPast() throws Exception {
		given(meetingCardService.get(10L, 8L)).willThrow(new BusinessException(ErrorCode.MEETING_NOT_PAST));

		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETING_NOT_PAST"));
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() throws Exception {
		given(meetingCardService.get(10L, 8L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
