package com.poco7250.moyeo.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.PlaceRouletteResponse;
import com.poco7250.moyeo.place.dto.PlaceRouletteResultResponse;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.service.PlaceCandidateService;
import com.poco7250.moyeo.place.service.PlaceRouletteService;
import com.poco7250.moyeo.place.service.RoundPlaceService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RoundPlaceController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = RoundPlaceController.class)
@Import(SecurityTestConfig.class)
class RoundPlaceControllerTest {

	private static final String CANDIDATES_URL = "/api/rounds/100/candidates";
	private static final String CONFIRM_URL = "/api/rounds/100/place/confirm-direct";
	private static final String MANUAL_CONFIRM_URL = "/api/rounds/100/place/confirm";
	private static final String VOTING_URL = "/api/rounds/100/place/voting";
	private static final String ROULETTE_URL = "/api/rounds/100/place/roulette";
	private static final String ROULETTES_URL = "/api/rounds/100/place/roulettes";
	private static final PlaceRouletteResponse ROULETTE = new PlaceRouletteResponse(900L, 100L, 12345L,
			List.of(501L, 500L), 501L, 7L, LocalDateTime.of(2026, 12, 19, 18, 1));
	private static final String VALID_PLACE = "{\"label\":\"강남 볼링\",\"manualAddress\":\"강남대로 1\"}";
	private static final CandidateResponse CANDIDATE = new CandidateResponse(500L, "강남 볼링", "강남대로 1", null,
			"https://map.kakao.com/link/search/%EA%B0%95%EB%82%A8%EB%8C%80%EB%A1%9C%201", 8L);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	PlaceCandidateService placeCandidateService;

	@MockitoBean
	RoundPlaceService roundPlaceService;

	@MockitoBean
	PlaceRouletteService placeRouletteService;

	@Test
	@DisplayName("후보 추가에 성공하면 201과 후보(지도 링크 포함)를 준다")
	void addCandidate() throws Exception {
		given(placeCandidateService.add(eq(100L), eq(8L), any(PlaceInput.class))).willReturn(CANDIDATE);

		mockMvc.perform(postJson(CANDIDATES_URL, VALID_PLACE).cookie(accessCookie(8L)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(500))
				.andExpect(jsonPath("$.data.label").value("강남 볼링"))
				.andExpect(jsonPath("$.data.mapUrl").value(CANDIDATE.mapUrl()))
				.andExpect(jsonPath("$.data.proposedByMemberId").value(8));
	}

	@Test
	@DisplayName("요청 본문의 카카오 장소 ID는 무시되고 사용자 입력만 서비스로 넘어간다")
	void kakaoFieldIgnored() throws Exception {
		given(placeCandidateService.add(eq(100L), eq(8L), any(PlaceInput.class))).willReturn(CANDIDATE);

		mockMvc.perform(postJson(CANDIDATES_URL,
						"{\"label\":\"강남 볼링\",\"kakaoPlaceId\":\"123\",\"manualMapUrl\":\"\"}")
						.cookie(accessCookie(8L)))
				.andExpect(status().isCreated());
		verify(placeCandidateService).add(100L, 8L, new PlaceInput("강남 볼링", null, null));
	}

	static List<String> invalidMapUrls() {
		return List.of("http://example.com", "javascript:alert(1)", "https://example.com/" + "a".repeat(2029));
	}

	@ParameterizedTest
	@MethodSource("invalidMapUrls")
	@DisplayName("지도 링크가 https가 아니거나 2,048자를 넘으면 400 manualMapUrl 필드 에러이고 저장하지 않는다")
	void invalidMapUrl(String url) throws Exception {
		String body = "{\"label\":\"강남 볼링\",\"manualMapUrl\":\"" + url + "\"}";

		mockMvc.perform(postJson(CANDIDATES_URL, body).cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("manualMapUrl"));
		mockMvc.perform(postJson(CONFIRM_URL, body).cookie(accessCookie(7L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("manualMapUrl"));
		verifyNoInteractions(placeCandidateService, roundPlaceService);
	}

	static List<String> invalidLabels() {
		return List.of("{}", "{\"label\":\"   \"}", "{\"label\":\"" + "가".repeat(51) + "\"}");
	}

	@ParameterizedTest
	@MethodSource("invalidLabels")
	@DisplayName("이름이 없거나 공백이거나 50자를 넘으면 400 label 필드 에러")
	void invalidLabel(String body) throws Exception {
		mockMvc.perform(postJson(CANDIDATES_URL, body).cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("label"));
		verifyNoInteractions(placeCandidateService);
	}

	@Test
	@DisplayName("주소가 200자를 넘으면 400 manualAddress 필드 에러")
	void addressTooLong() throws Exception {
		String body = "{\"label\":\"고깃집\",\"manualAddress\":\"" + "가".repeat(201) + "\"}";

		mockMvc.perform(postJson(CANDIDATES_URL, body).cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("manualAddress"));
	}

	@Test
	@DisplayName("투표 중이 아닌 차수에 후보를 추가하면 409 ROUND_PLACE_INVALID_TRANSITION")
	void addCandidateNotVoting() throws Exception {
		given(placeCandidateService.add(eq(100L), eq(8L), any(PlaceInput.class)))
				.willThrow(new BusinessException(ErrorCode.ROUND_PLACE_INVALID_TRANSITION));

		mockMvc.perform(postJson(CANDIDATES_URL, VALID_PLACE).cookie(accessCookie(8L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.ROUND_PLACE_INVALID_TRANSITION.name()));
	}

	@Test
	@DisplayName("바로 확정에 성공하면 200과 차수 장소 상태를 준다")
	void confirmDirectly() throws Exception {
		given(roundPlaceService.confirmDirectly(eq(100L), eq(7L), any(PlaceInput.class)))
				.willReturn(new RoundPlaceResponse(100L, PlaceStatus.CONFIRMED, null, false, 500L, List.of(CANDIDATE)));

		mockMvc.perform(postJson(CONFIRM_URL, VALID_PLACE).cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roundId").value(100))
				.andExpect(jsonPath("$.data.placeStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.confirmedCandidateId").value(500))
				.andExpect(jsonPath("$.data.candidates[0].label").value("강남 볼링"));
	}

	@Test
	@DisplayName("호스트가 아니면 바로 확정은 403 NOT_MEETING_HOST")
	void confirmDirectlyByNonHost() throws Exception {
		given(roundPlaceService.confirmDirectly(eq(100L), eq(8L), any(PlaceInput.class)))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		mockMvc.perform(postJson(CONFIRM_URL, VALID_PLACE).cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_MEETING_HOST.name()));
	}

	@Test
	@DisplayName("수동 확정에 성공하면 200과 확정된 차수 장소 상태를 준다")
	void confirmManually() throws Exception {
		given(roundPlaceService.confirmManually(100L, 7L, 500L))
				.willReturn(new RoundPlaceResponse(100L, PlaceStatus.CONFIRMED, null, false, 500L, List.of(CANDIDATE)));

		mockMvc.perform(postJson(MANUAL_CONFIRM_URL, "{\"candidateId\":500}").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.placeStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.hostDecisionRequired").value(false))
				.andExpect(jsonPath("$.data.confirmedCandidateId").value(500));
	}

	@Test
	@DisplayName("수동 확정에 후보 id가 없으면 400 candidateId 필드 에러")
	void confirmManuallyWithoutCandidate() throws Exception {
		mockMvc.perform(postJson(MANUAL_CONFIRM_URL, "{}").cookie(accessCookie(7L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("candidateId"));
		verifyNoInteractions(roundPlaceService);
	}

	@Test
	@DisplayName("비로그인 수동 확정은 401")
	void confirmManuallyUnauthenticated() throws Exception {
		mockMvc.perform(postJson(MANUAL_CONFIRM_URL, "{\"candidateId\":500}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(roundPlaceService);
	}

	static Stream<Arguments> confirmManuallyFailures() {
		return Stream.of(
				Arguments.of(new BusinessException(ErrorCode.NOT_MEETING_HOST), 403, ErrorCode.NOT_MEETING_HOST),
				Arguments.of(new BusinessException(ErrorCode.PLACE_CANDIDATE_NOT_FOUND), 404,
						ErrorCode.PLACE_CANDIDATE_NOT_FOUND),
				Arguments.of(new BusinessException(ErrorCode.ROUND_PLACE_INVALID_TRANSITION), 409,
						ErrorCode.ROUND_PLACE_INVALID_TRANSITION),
				Arguments.of(new ObjectOptimisticLockingFailureException(Round.class, 100L), 409,
						ErrorCode.CONCURRENT_MODIFICATION));
	}

	@ParameterizedTest
	@MethodSource("confirmManuallyFailures")
	@DisplayName("수동 확정 실패는 권한 403, 없는 후보 404, 이미 확정·동시 충돌 409")
	void confirmManuallyFails(RuntimeException failure, int expectedStatus, ErrorCode expectedCode) throws Exception {
		given(roundPlaceService.confirmManually(100L, 7L, 500L)).willThrow(failure);

		mockMvc.perform(postJson(MANUAL_CONFIRM_URL, "{\"candidateId\":500}").cookie(accessCookie(7L)))
				.andExpect(status().is(expectedStatus))
				.andExpect(jsonPath("$.error.code").value(expectedCode.name()));
	}

	@Test
	@DisplayName("투표 열기에 성공하면 200과 VOTING·마감 시각을 준다")
	void openVoting() throws Exception {
		LocalDateTime deadline = LocalDateTime.now().plusDays(1).withNano(0);
		given(roundPlaceService.openVoting(100L, 7L, deadline))
				.willReturn(new RoundPlaceResponse(100L, PlaceStatus.VOTING, deadline, false, null, List.of()));

		mockMvc.perform(postJson(VOTING_URL, "{\"placeVoteDeadline\":\"" + deadline + "\"}").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.placeStatus").value("VOTING"))
				.andExpect(jsonPath("$.data.confirmedCandidateId").doesNotExist())
				.andExpect(jsonPath("$.data.candidates").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"placeVoteDeadline\":\"2020-01-01T00:00:00\"}"})
	@DisplayName("투표 마감 시각이 없거나 과거면 400 placeVoteDeadline 필드 에러")
	void invalidDeadline(String body) throws Exception {
		mockMvc.perform(postJson(VOTING_URL, body).cookie(accessCookie(7L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("placeVoteDeadline"));
		verifyNoInteractions(roundPlaceService);
	}

	@Test
	@DisplayName("비로그인은 401")
	void unauthenticated() throws Exception {
		mockMvc.perform(postJson(CANDIDATES_URL, VALID_PLACE)).andExpect(status().isUnauthorized());
		mockMvc.perform(postJson(CONFIRM_URL, VALID_PLACE)).andExpect(status().isUnauthorized());
		verifyNoInteractions(placeCandidateService, roundPlaceService);
	}

	@Test
	@DisplayName("비멤버·없는 차수는 403 NOT_GROUP_MEMBER")
	void nonMember() throws Exception {
		given(placeCandidateService.add(eq(100L), eq(9L), any(PlaceInput.class)))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(postJson(CANDIDATES_URL, VALID_PLACE).cookie(accessCookie(9L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	@Test
	@DisplayName("룰렛을 돌리면 확정된 장소 상태와 룰렛 기록(seed·추첨 순서·당첨 후보)을 준다")
	void spinRoulette() throws Exception {
		RoundPlaceResponse place = new RoundPlaceResponse(100L, PlaceStatus.CONFIRMED, null, false, 501L, List.of());
		given(placeRouletteService.spin(100L, 7L)).willReturn(new PlaceRouletteResultResponse(place, ROULETTE));

		mockMvc.perform(post(ROULETTE_URL).cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.place.placeStatus").value("CONFIRMED"))
				.andExpect(jsonPath("$.data.place.confirmedCandidateId").value(501))
				.andExpect(jsonPath("$.data.roulette.seed").value(12345))
				.andExpect(jsonPath("$.data.roulette.candidateIds[0]").value(501))
				.andExpect(jsonPath("$.data.roulette.candidateIds[1]").value(500))
				.andExpect(jsonPath("$.data.roulette.winnerCandidateId").value(501))
				.andExpect(jsonPath("$.data.roulette.spunByMemberId").value(7));
	}

	static Stream<Arguments> rouletteErrors() {
		return Stream.of(
				Arguments.of(ErrorCode.NOT_MEETING_HOST, 403),
				Arguments.of(ErrorCode.ROUND_PLACE_INVALID_TRANSITION, 409),
				Arguments.of(ErrorCode.PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES, 409));
	}

	@ParameterizedTest
	@MethodSource("rouletteErrors")
	@DisplayName("룰렛 서비스 예외는 상태 코드·에러 코드로 그대로 전달된다")
	void spinRouletteErrors(ErrorCode errorCode, int status) throws Exception {
		given(placeRouletteService.spin(100L, 8L)).willThrow(new BusinessException(errorCode));

		mockMvc.perform(post(ROULETTE_URL).cookie(accessCookie(8L)))
				.andExpect(status().is(status))
				.andExpect(jsonPath("$.error.code").value(errorCode.name()));
	}

	@Test
	@DisplayName("룰렛이 수동 확정과 충돌하면 409 CONCURRENT_MODIFICATION")
	void spinRouletteConflict() throws Exception {
		given(placeRouletteService.spin(100L, 7L))
				.willThrow(new ObjectOptimisticLockingFailureException(Round.class, 100L));

		mockMvc.perform(post(ROULETTE_URL).cookie(accessCookie(7L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.CONCURRENT_MODIFICATION.name()));
	}

	@Test
	@DisplayName("룰렛 기록을 최근 순 목록으로 준다")
	void getRoulettes() throws Exception {
		given(placeRouletteService.getHistory(100L, 8L)).willReturn(List.of(ROULETTE));

		mockMvc.perform(get(ROULETTES_URL).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(900))
				.andExpect(jsonPath("$.data[0].roundId").value(100))
				.andExpect(jsonPath("$.data[0].spunAt").value("2026-12-19T18:01:00"));
	}

	@Test
	@DisplayName("비로그인 룰렛 돌리기·기록 조회는 401")
	void rouletteUnauthenticated() throws Exception {
		mockMvc.perform(post(ROULETTE_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ROULETTES_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(placeRouletteService);
	}

	@Test
	@DisplayName("장소 조회는 상태·후보·확정 후보를 준다")
	void getPlace() throws Exception {
		given(roundPlaceService.get(100L, 8L)).willReturn(new RoundPlaceResponse(100L, PlaceStatus.VOTING,
				LocalDateTime.of(2026, 12, 19, 18, 0), false, null, List.of(CANDIDATE)));

		mockMvc.perform(get("/api/rounds/100/place").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roundId").value(100))
				.andExpect(jsonPath("$.data.placeStatus").value("VOTING"))
				.andExpect(jsonPath("$.data.placeVoteDeadline").value("2026-12-19T18:00:00"))
				.andExpect(jsonPath("$.data.confirmedCandidateId").doesNotExist())
				.andExpect(jsonPath("$.data.candidates[0].id").value(500))
				.andExpect(jsonPath("$.data.candidates[0].mapUrl").value(CANDIDATE.mapUrl()));
	}

	@Test
	@DisplayName("비멤버가 장소를 조회하면 403, 비로그인이면 401")
	void getPlaceDenied() throws Exception {
		given(roundPlaceService.get(100L, 9L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/rounds/100/place").cookie(accessCookie(9L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
		mockMvc.perform(get("/api/rounds/100/place"))
				.andExpect(status().isUnauthorized());
	}

	private static MockHttpServletRequestBuilder postJson(String url, String body) {
		return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
