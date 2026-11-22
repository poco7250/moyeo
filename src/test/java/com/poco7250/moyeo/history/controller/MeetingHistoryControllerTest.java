package com.poco7250.moyeo.history.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.history.dto.BoardGameHistoryResponse;
import com.poco7250.moyeo.history.dto.BoardGameRankHistoryResponse;
import com.poco7250.moyeo.history.dto.GameHistoryResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryPageResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryResponse;
import com.poco7250.moyeo.history.dto.RoundHistoryResponse;
import com.poco7250.moyeo.history.service.MeetingHistoryService;
import com.poco7250.moyeo.meeting.dto.ConfirmedPlaceResponse;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MeetingHistoryController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = MeetingHistoryController.class)
@Import(SecurityTestConfig.class)
class MeetingHistoryControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	MeetingHistoryService meetingHistoryService;

	@Test
	@DisplayName("기본 0페이지·20건이고, 차수별 장소·참석자·게임 벌칙자가 공통 응답 래퍼로 내려간다")
	void list() throws Exception {
		HistoryMemberResponse minsu = new HistoryMemberResponse(3L, "민수", "🎳");
		HistoryMemberResponse jiho = new HistoryMemberResponse(4L, "지호", null);
		RoundHistoryResponse round = new RoundHistoryResponse(20L, 1, "볼링",
				new ConfirmedPlaceResponse("강남 볼링장", "https://map.kakao.com/link/search/강남 볼링장"),
				List.of(minsu), List.of(jiho), List.of(new GameHistoryResponse(30L, GameType.LADDER, PenaltyKind.MONEY, "꽝이 볼링비",
						LocalDateTime.of(2026, 11, 1, 21, 0), List.of(minsu), List.of())),
				List.of(new BoardGameHistoryResponse(40L, "스플렌더", List.of(new BoardGameRankHistoryResponse(1, minsu)), minsu)));
		given(meetingHistoryService.list(5L, 8L, 0, 20)).willReturn(new MeetingHistoryPageResponse(List.of(
				new MeetingHistoryResponse(10L, "11월 모임", LocalDateTime.of(2026, 11, 1, 18, 0), 1, 87_650L,
						SettlementStatus.CONFIRMED, 2, List.of(round))), 0, 20, true));

		mockMvc.perform(get("/api/groups/5/history").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.items[0].startAt").value("2026-11-01T18:00:00"))
				.andExpect(jsonPath("$.data.items[0].totalExpenseAmount").value(87_650))
				.andExpect(jsonPath("$.data.items[0].settlementStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.items[0].settlementRevisionNo").value(2))
				.andExpect(jsonPath("$.data.items[0].rounds[0].place.label").value("강남 볼링장"))
				.andExpect(jsonPath("$.data.items[0].rounds[0].attendees[0].nickname").value("민수"))
				.andExpect(jsonPath("$.data.items[0].rounds[0].noShows[0].nickname").value("지호"))
				.andExpect(jsonPath("$.data.items[0].rounds[0].games[0].type").value("LADDER"))
				.andExpect(jsonPath("$.data.items[0].rounds[0].games[0].penalized[0].memberId").value(3))
				.andExpect(jsonPath("$.data.items[0].rounds[0].boardGames[0].gameName").value("스플렌더"))
				.andExpect(jsonPath("$.data.items[0].rounds[0].boardGames[0].ranks[0].rank").value(1))
				.andExpect(jsonPath("$.data.items[0].rounds[0].boardGames[0].ranks[0].member.nickname").value("민수"))
				.andExpect(jsonPath("$.data.items[0].rounds[0].boardGames[0].loser.memberId").value(3));
	}

	@Test
	@DisplayName("page·size를 넘기면 그대로 서비스에 전달한다")
	void passesPaging() throws Exception {
		given(meetingHistoryService.list(5L, 8L, 2, 50))
				.willReturn(new MeetingHistoryPageResponse(List.of(), 2, 50, false));

		mockMvc.perform(get("/api/groups/5/history").param("page", "2").param("size", "50").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@ParameterizedTest
	@CsvSource({"-1,20", "0,0", "0,51"})
	@DisplayName("page가 음수이거나 size가 1~50 밖이면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void invalidPaging(String page, String size) throws Exception {
		mockMvc.perform(get("/api/groups/5/history").param("page", page).param("size", size).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(meetingHistoryService);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() throws Exception {
		given(meetingHistoryService.list(5L, 8L, 0, 20)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/groups/5/history").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(get("/api/groups/5/history"))
				.andExpect(status().isUnauthorized());
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
