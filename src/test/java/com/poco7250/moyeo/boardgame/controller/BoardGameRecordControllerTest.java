package com.poco7250.moyeo.boardgame.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.boardgame.dto.BoardGameRankRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRankResponse;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordResponse;
import com.poco7250.moyeo.boardgame.service.BoardGameRecordService;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * BoardGameRecordController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = BoardGameRecordController.class)
@Import(SecurityTestConfig.class)
class BoardGameRecordControllerTest {

	private static final String ROUND_URL = "/api/rounds/100/board-games";
	private static final String RECORD_URL = "/api/board-games/300";
	private static final String VALID_BODY =
			"{\"gameName\":\"스플렌더\",\"ranks\":[{\"memberId\":8,\"rank\":1},{\"memberId\":9,\"rank\":1}],\"loserMemberId\":9}";
	private static final LocalDateTime AT = LocalDateTime.of(2026, 12, 20, 21, 0);
	private static final BoardGameRecordResponse RECORD = new BoardGameRecordResponse(300L, 100L, "스플렌더",
			List.of(new BoardGameRankResponse(8L, 1), new BoardGameRankResponse(9L, 1)), 9L, 8L, AT, AT);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	BoardGameRecordService boardGameRecordService;

	@Test
	@DisplayName("기록 생성에 성공하면 201과 기록(순위 포함)을 준다")
	void create() throws Exception {
		given(boardGameRecordService.create(eq(100L), eq(8L), any(BoardGameRecordRequest.class))).willReturn(RECORD);

		mockMvc.perform(json(post(ROUND_URL), VALID_BODY).cookie(accessCookie(8L)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(300))
				.andExpect(jsonPath("$.data.gameName").value("스플렌더"))
				.andExpect(jsonPath("$.data.ranks[1].memberId").value(9))
				.andExpect(jsonPath("$.data.ranks[1].rank").value(1))
				.andExpect(jsonPath("$.data.loserMemberId").value(9))
				.andExpect(jsonPath("$.data.recordedByMemberId").value(8))
				.andExpect(jsonPath("$.data.createdAt").value("2026-12-20T21:00:00"));
		verify(boardGameRecordService).create(100L, 8L, new BoardGameRecordRequest("스플렌더",
				List.of(new BoardGameRankRequest(8L, 1), new BoardGameRankRequest(9L, 1)), 9L));
	}

	@Test
	@DisplayName("차수의 기록 목록을 돌려준다")
	void list() throws Exception {
		given(boardGameRecordService.list(100L, 8L)).willReturn(List.of(RECORD));

		mockMvc.perform(get(ROUND_URL).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(300))
				.andExpect(jsonPath("$.data[0].roundId").value(100));
	}

	@Test
	@DisplayName("수정·삭제는 서비스 결과를 그대로 돌려준다")
	void updateAndDelete() throws Exception {
		given(boardGameRecordService.update(eq(300L), eq(8L), any(BoardGameRecordRequest.class))).willReturn(RECORD);

		mockMvc.perform(json(put(RECORD_URL), VALID_BODY).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(300));
		mockMvc.perform(delete(RECORD_URL).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		verify(boardGameRecordService).delete(300L, 8L);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{\"gameName\":\" \",\"ranks\":[{\"memberId\":8,\"rank\":1},{\"memberId\":9,\"rank\":2}]}|gameName",
			"{\"gameName\":\"" + "가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가가"
					+ "\",\"ranks\":[{\"memberId\":8,\"rank\":1},{\"memberId\":9,\"rank\":2}]}|gameName",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"memberId\":8,\"rank\":1}]}|ranks",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"memberId\":8,\"rank\":1},{\"memberId\":8,\"rank\":2}]}|membersDistinct",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"memberId\":8,\"rank\":0},{\"memberId\":9,\"rank\":1}]}|ranks[0].rank",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"memberId\":8,\"rank\":3},{\"memberId\":9,\"rank\":1}]}|rankWithinCount",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"rank\":1},{\"memberId\":9,\"rank\":1}]}|ranks[0].memberId",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"memberId\":8,\"rank\":1},{\"memberId\":9,\"rank\":2}]}|loserMemberId",
			"{\"gameName\":\"카탄\",\"ranks\":[{\"memberId\":8,\"rank\":1},{\"memberId\":9,\"rank\":2}],"
					+ "\"loserMemberId\":10}|loserParticipant"
	})
	@DisplayName("게임명·참가자 수·중복 회원·순위 범위·꼴등(누락·참가자 아님)을 어기면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void invalidRequest(String body, String field) throws Exception {
		mockMvc.perform(json(post(ROUND_URL), body).cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem(field)));
		verifyNoInteractions(boardGameRecordService);
	}

	@Test
	@DisplayName("기록한 사람·호스트가 아니면 403 NOT_BOARD_GAME_RECORDER, 참석자가 아니면 400을 전달한다")
	void forwardsBusinessErrors() throws Exception {
		given(boardGameRecordService.update(eq(300L), eq(9L), any(BoardGameRecordRequest.class)))
				.willThrow(new BusinessException(ErrorCode.NOT_BOARD_GAME_RECORDER));
		given(boardGameRecordService.create(eq(100L), eq(8L), any(BoardGameRecordRequest.class)))
				.willThrow(new BusinessException(ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING));

		mockMvc.perform(json(put(RECORD_URL), VALID_BODY).cookie(accessCookie(9L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_BOARD_GAME_RECORDER"));
		mockMvc.perform(json(post(ROUND_URL), VALID_BODY).cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("GAME_PARTICIPANT_NOT_ATTENDING"));
	}

	@Test
	@DisplayName("동시 수정·삭제가 충돌하면 409 CONCURRENT_MODIFICATION")
	void optimisticConflict() throws Exception {
		willThrow(new ObjectOptimisticLockingFailureException(BoardGameRecord.class, 300L))
				.given(boardGameRecordService).delete(300L, 8L);

		mockMvc.perform(delete(RECORD_URL).cookie(accessCookie(8L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
	}

	@Test
	@DisplayName("비로그인은 401")
	void unauthenticated() throws Exception {
		mockMvc.perform(get(ROUND_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(json(post(ROUND_URL), VALID_BODY)).andExpect(status().isUnauthorized());
		mockMvc.perform(json(put(RECORD_URL), VALID_BODY)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(RECORD_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(boardGameRecordService);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
