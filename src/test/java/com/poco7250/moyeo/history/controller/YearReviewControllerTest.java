package com.poco7250.moyeo.history.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.history.dto.AttendanceRankResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.NoShowRankResponse;
import com.poco7250.moyeo.history.dto.PenaltyRankResponse;
import com.poco7250.moyeo.history.dto.PlaceVisitResponse;
import com.poco7250.moyeo.history.dto.ShareRankResponse;
import com.poco7250.moyeo.history.dto.YearReviewResponse;
import com.poco7250.moyeo.history.service.YearReviewService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * YearReviewController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = YearReviewController.class)
@Import(SecurityTestConfig.class)
class YearReviewControllerTest {

	private static final String URL = "/api/groups/5/year-review";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	YearReviewService yearReviewService;

	@Test
	@DisplayName("연말 결산을 공통 응답 래퍼로 준다")
	void response() throws Exception {
		HistoryMemberResponse minsu = new HistoryMemberResponse(3L, "민수", "🎳", false);
		given(yearReviewService.get(5L, 8L, 2026)).willReturn(new YearReviewResponse(2026, 23, 30, 1_420_000L, 20,
				List.of(new AttendanceRankResponse(1, minsu, 20)), List.of(new ShareRankResponse(1, minsu, 300_000L)),
				List.of(new PlaceVisitResponse(1, "강남 OO식당", 5, LocalDateTime.of(2026, 11, 1, 18, 0))),
				List.of(new PenaltyRankResponse(1, minsu, 7, 52_000L)), List.of(new NoShowRankResponse(1, minsu, 2)),
				"[대학 동기] 2026년 결산"));

		mockMvc.perform(get(URL).param("year", "2026").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.year").value(2026))
				.andExpect(jsonPath("$.data.meetingCount").value(23))
				.andExpect(jsonPath("$.data.roundCount").value(30))
				.andExpect(jsonPath("$.data.totalExpense").value(1_420_000))
				.andExpect(jsonPath("$.data.confirmedMeetingCount").value(20))
				.andExpect(jsonPath("$.data.attendanceRanking[0].attendCount").value(20))
				.andExpect(jsonPath("$.data.shareRanking[0].shareAmount").value(300_000))
				.andExpect(jsonPath("$.data.topPlaces[0].label").value("강남 OO식당"))
				.andExpect(jsonPath("$.data.topPlaces[0].lastVisitedAt").value("2026-11-01T18:00:00"))
				.andExpect(jsonPath("$.data.penaltyKings[0].member.nickname").value("민수"))
				.andExpect(jsonPath("$.data.noShowKings[0].noShowCount").value(2))
				.andExpect(jsonPath("$.data.shareText").value("[대학 동기] 2026년 결산"));
	}

	@Test
	@DisplayName("year가 없으면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void missingYear() throws Exception {
		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(yearReviewService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1999", "3000", "abc"})
	@DisplayName("year가 2000~2999 밖이거나 숫자가 아니면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void invalidYear(String year) throws Exception {
		mockMvc.perform(get(URL).param("year", year).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(yearReviewService);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() throws Exception {
		given(yearReviewService.get(5L, 8L, 2026)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get(URL).param("year", "2026").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(get(URL).param("year", "2026")).andExpect(status().isUnauthorized());
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
