package com.poco7250.moyeo.game.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameParticipantResponse;
import com.poco7250.moyeo.game.dto.GameResultResponse;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.game.service.GameQueryService;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GameController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = GameController.class)
@Import(SecurityTestConfig.class)
class GameControllerTest {

	private static final Long MEMBER_ID = 8L;
	private static final String CREATE_URL = "/api/rounds/100/games";
	private static final List<GameParticipantResponse> PARTICIPANTS = List.of(
			new GameParticipantResponse(8L, "민수", "🍖"), new GameParticipantResponse(9L, "영희", null));
	private static final GameStateResponse WAITING = new GameStateResponse(300L, 100L, GameType.LADDER,
			GameStatus.WAITING, MEMBER_ID, PenaltyKind.MONEY, "꼴찌가 2차 30%", 1, PARTICIPANTS, null);
	private static final GameStateResponse REVEALED = new GameStateResponse(300L, 100L, GameType.LADDER,
			GameStatus.REVEALED, MEMBER_ID, PenaltyKind.MONEY, "꼴찌가 2차 30%", 1, PARTICIPANTS,
			new GameResultResponse(42L, List.of(9L)));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	GameLobbyService gameLobbyService;

	@MockitoBean
	GameQueryService gameQueryService;

	@Test
	@DisplayName("공개 전 상태 조회 응답에는 결과와 seed가 없다")
	void stateBeforeReveal() throws Exception {
		given(gameQueryService.getState(300L, MEMBER_ID)).willReturn(WAITING);

		query("/api/games/300/state")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("WAITING"))
				.andExpect(jsonPath("$.data.participants[1].nickname").value("영희"))
				.andExpect(jsonPath("$.data.result").doesNotExist())
				.andExpect(jsonPath("$.data.seed").doesNotExist())
				.andExpect(jsonPath("$.data.resultData").doesNotExist());
	}

	@Test
	@DisplayName("공개 후 상태 조회 응답에는 seed와 벌칙자가 있다")
	void stateAfterReveal() throws Exception {
		given(gameQueryService.getState(300L, MEMBER_ID)).willReturn(REVEALED);

		query("/api/games/300/state")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REVEALED"))
				.andExpect(jsonPath("$.data.result.seed").value(42))
				.andExpect(jsonPath("$.data.result.penaltyMemberIds[0]").value(9));
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 상태 조회는 403 NOT_GROUP_MEMBER다")
	void stateForbidden() throws Exception {
		given(gameQueryService.getState(300L, MEMBER_ID)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		query("/api/games/300/state")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("차수 게임 목록은 공개된 게임만 결과를 담는다")
	void roundGames() throws Exception {
		given(gameQueryService.getRoundGames(100L, MEMBER_ID)).willReturn(List.of(WAITING, REVEALED));

		query(CREATE_URL)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].result").doesNotExist())
				.andExpect(jsonPath("$.data[1].result.penaltyMemberIds[0]").value(9));
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 차수 게임 목록은 403 NOT_GROUP_MEMBER다")
	void roundGamesForbidden() throws Exception {
		given(gameQueryService.getRoundGames(100L, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		query(CREATE_URL)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("로그인하지 않으면 조회도 401이다")
	void queryUnauthenticated() throws Exception {
		mockMvc.perform(get("/api/games/300/state")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(CREATE_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(gameQueryService);
	}

	@Test
	@DisplayName("게임을 만들면 201과 안내 문구를 주고 공개 전 결과는 없다")
	void createResponse() throws Exception {
		given(gameLobbyService.create(eq(100L), eq(MEMBER_ID), any())).willReturn(WAITING);

		create(body("\"꼴찌가 2차 30%\"", "[8, 9]", "1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("WAITING"))
				.andExpect(jsonPath("$.data.penaltyNote").value("꼴찌가 2차 30%"))
				.andExpect(jsonPath("$.data.participants[0].nickname").value("민수"))
				.andExpect(jsonPath("$.data.result").doesNotExist())
				.andExpect(jsonPath("$.data.seed").doesNotExist())
				.andExpect(jsonPath("$.data.resultData").doesNotExist());
	}

	@Test
	@DisplayName("안내 문구가 101자면 400이다")
	void noteTooLong() throws Exception {
		assertInvalid(body("\"" + "가".repeat(101) + "\"", "[8, 9]", "1"), "penaltyNote");
	}

	@Test
	@DisplayName("참가자가 2명 미만이면 400이다")
	void tooFewParticipants() throws Exception {
		assertInvalid(body("null", "[8]", "null"), "participantMemberIds");
	}

	@Test
	@DisplayName("같은 참가자를 두 번 고르면 400이다")
	void duplicatedParticipants() throws Exception {
		assertInvalid(body("null", "[8, 8]", "1"), "participantsDistinct");
	}

	@Test
	@DisplayName("벌칙자 수가 참가자 수와 같으면 400이다")
	void penaltyCountTooLarge() throws Exception {
		assertInvalid(body("null", "[8, 9]", "2"), "penaltyCountValid");
	}

	@Test
	@DisplayName("벌칙자 수가 0이면 400이다")
	void penaltyCountZero() throws Exception {
		assertInvalid(body("null", "[8, 9]", "0"), "penaltyCountValid");
	}

	@Test
	@DisplayName("시작하면 결과와 seed를 돌려준다")
	void startResponse() throws Exception {
		given(gameLobbyService.start(300L, MEMBER_ID)).willReturn(REVEALED);

		action("start")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REVEALED"))
				.andExpect(jsonPath("$.data.result.seed").value(42))
				.andExpect(jsonPath("$.data.result.penaltyMemberIds[0]").value(9));
	}

	@Test
	@DisplayName("권한이 없으면 403 NOT_GAME_OPERATOR다")
	void notOperator() throws Exception {
		given(gameLobbyService.start(300L, MEMBER_ID)).willThrow(new BusinessException(ErrorCode.NOT_GAME_OPERATOR));

		action("start")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GAME_OPERATOR"));
	}

	@Test
	@DisplayName("종료·취소 요청을 서비스로 넘긴다")
	void closeAndCancel() throws Exception {
		given(gameLobbyService.close(300L, MEMBER_ID)).willReturn(REVEALED);
		given(gameLobbyService.cancel(300L, MEMBER_ID)).willThrow(new BusinessException(ErrorCode.GAME_NOT_CANCELABLE));

		action("close").andExpect(status().isOk());
		action("cancel")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GAME_NOT_CANCELABLE"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401이다")
	void unauthenticated() throws Exception {
		mockMvc.perform(post("/api/games/300/start"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(gameLobbyService);
	}

	private void assertInvalid(String body, String field) throws Exception {
		create(body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				// 한 요청이 여러 검증에 걸리면 필드 에러 순서가 정해져 있지 않다
				.andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem(field)));
		verifyNoInteractions(gameLobbyService);
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post(CREATE_URL).cookie(loginCookie())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions query(String url) throws Exception {
		return mockMvc.perform(get(url).cookie(loginCookie()));
	}

	private ResultActions action(String name) throws Exception {
		return mockMvc.perform(post("/api/games/300/" + name).cookie(loginCookie()));
	}

	private String body(String note, String memberIds, String penaltyCount) {
		return """
				{"type": "LADDER", "penaltyKind": "MONEY", "penaltyNote": %s,
				 "participantMemberIds": %s, "penaltyCount": %s}
				""".formatted(note, memberIds, penaltyCount);
	}

	private Cookie loginCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(MEMBER_ID));
	}
}
