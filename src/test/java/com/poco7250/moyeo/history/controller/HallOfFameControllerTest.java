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
import com.poco7250.moyeo.history.dto.AttendanceLogResponse;
import com.poco7250.moyeo.history.dto.BoardGameLoserRankResponse;
import com.poco7250.moyeo.history.dto.HallOfFameResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.NoShowRankResponse;
import com.poco7250.moyeo.history.dto.PenaltyRankResponse;
import com.poco7250.moyeo.history.service.HallOfFameService;
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
 * HallOfFameController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = HallOfFameController.class)
@Import(SecurityTestConfig.class)
class HallOfFameControllerTest {

	private static final String URL = "/api/groups/5/hall-of-fame";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	HallOfFameService hallOfFameService;

	@Test
	@DisplayName("year 없이 부르면 전체 기간으로 벌칙 순위·보드게임 꼴등 순위·노쇼 순위·출석부를 공통 응답 래퍼로 준다")
	void allTime() throws Exception {
		HistoryMemberResponse minsu = new HistoryMemberResponse(3L, "민수", "🎳", false);
		HistoryMemberResponse jiho = new HistoryMemberResponse(4L, "지호", null, false);
		given(hallOfFameService.get(5L, 8L, null)).willReturn(new HallOfFameResponse(null,
				List.of(new PenaltyRankResponse(1, minsu, 4, 52_000L)),
				List.of(new BoardGameLoserRankResponse(1, minsu, 2, 5)),
				List.of(new NoShowRankResponse(1, jiho, 1), new NoShowRankResponse(2, minsu, 0)),
				List.of(new AttendanceLogResponse(10L, "11월 모임", LocalDateTime.of(2026, 11, 1, 18, 0),
						List.of(minsu), List.of(jiho)))));

		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.year").doesNotExist())
				.andExpect(jsonPath("$.data.penaltyRanking[0].rank").value(1))
				.andExpect(jsonPath("$.data.penaltyRanking[0].member.nickname").value("민수"))
				.andExpect(jsonPath("$.data.penaltyRanking[0].penaltyCount").value(4))
				.andExpect(jsonPath("$.data.penaltyRanking[0].penaltyAmount").value(52_000))
				.andExpect(jsonPath("$.data.boardGameLoserRanking[0].loserCount").value(2))
				.andExpect(jsonPath("$.data.boardGameLoserRanking[0].playCount").value(5))
				.andExpect(jsonPath("$.data.attendanceLog[0].startAt").value("2026-11-01T18:00:00"))
				.andExpect(jsonPath("$.data.noShowRanking[0].member.nickname").value("지호"))
				.andExpect(jsonPath("$.data.noShowRanking[0].noShowCount").value(1))
				.andExpect(jsonPath("$.data.noShowRanking[1].rank").value(2))
				.andExpect(jsonPath("$.data.attendanceLog[0].attendees[0].memberId").value(3))
				.andExpect(jsonPath("$.data.attendanceLog[0].noShows[0].memberId").value(4));
	}

	@Test
	@DisplayName("year를 넘기면 그대로 서비스에 전달한다")
	void passesYear() throws Exception {
		given(hallOfFameService.get(5L, 8L, 2026))
				.willReturn(new HallOfFameResponse(2026, List.of(), List.of(), List.of(), List.of()));

		mockMvc.perform(get(URL).param("year", "2026").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.year").value(2026));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1999", "3000"})
	@DisplayName("year가 2000~2999 밖이면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void invalidYear(String year) throws Exception {
		mockMvc.perform(get(URL).param("year", year).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(hallOfFameService);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() throws Exception {
		given(hallOfFameService.get(5L, 8L, null)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

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
