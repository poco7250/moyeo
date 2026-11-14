package com.poco7250.moyeo.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.settlement.dto.ExpenseDeleteResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 시나리오 통합 테스트에서 실제 HTTP API를 부르는 얇은 래퍼. 메서드 하나가 API 한 번이다.
 * 로그인은 회원 id로 만든 Access 토큰을 쿠키에 실어 대신한다.
 */
public final class ScenarioApi {

	private final MockMvc mockMvc;
	private final JwtTokenProvider jwtTokenProvider;
	private final JsonMapper jsonMapper;

	public ScenarioApi(MockMvc mockMvc, JwtTokenProvider jwtTokenProvider, JsonMapper jsonMapper) {
		this.mockMvc = mockMvc;
		this.jwtTokenProvider = jwtTokenProvider;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * 실패한 API 응답의 상태 코드와 에러 본문.
	 */
	public record ApiError(int status, String code, JsonNode details) {
	}

	private record Exchange(int status, JsonNode body) {
	}

	/**
	 * 그룹을 만든다. 만든 회원이 멤버가 되고 초대 링크가 함께 온다.
	 */
	public GroupCreateResponse createGroup(Long memberId, String name) {
		return ok(HttpMethod.POST, "/api/groups", memberId, Map.of("name", name), GroupCreateResponse.class);
	}

	/**
	 * 초대 링크로 그룹에 가입한다.
	 */
	public void join(Long memberId, String inviteToken) {
		okData(HttpMethod.POST, "/api/invites/" + inviteToken + "/join", memberId, null);
	}

	/**
	 * 차수 템플릿으로 모임을 만든다. 만든 회원이 호스트다.
	 */
	public MeetingDetailResponse createMeeting(Long memberId, Long groupId, String title, RoundTemplate template) {
		Map<String, Object> body = Map.of("title", title, "startAt", "2099-12-20T18:00:00", "template", template);
		return ok(HttpMethod.POST, "/api/groups/" + groupId + "/meetings", memberId, body,
				MeetingDetailResponse.class);
	}

	/**
	 * 차수 하나에 내 참석 응답을 보낸다.
	 */
	public void attend(Long memberId, Long meetingId, Long roundId, AttendanceStatus status) {
		Map<String, Object> body = Map.of("rounds", List.of(Map.of("roundId", roundId, "status", status)));
		okData(HttpMethod.PUT, "/api/meetings/" + meetingId + "/attendances/me", memberId, body);
	}

	/**
	 * 차수에 지출을 등록한다.
	 */
	public ExpenseResponse addExpense(Long memberId, Long roundId, long amount, Long payerId, String memo) {
		Map<String, Object> body = Map.of("amount", amount, "payerId", payerId, "memo", memo);
		return ok(HttpMethod.POST, "/api/rounds/" + roundId + "/expenses", memberId, body, ExpenseResponse.class);
	}

	/**
	 * 지출을 삭제한다.
	 */
	public ExpenseDeleteResponse deleteExpense(Long memberId, Long expenseId) {
		return ok(HttpMethod.DELETE, "/api/expenses/" + expenseId, memberId, null, ExpenseDeleteResponse.class);
	}

	/**
	 * 금액형 게임 방을 만든다.
	 */
	public GameStateResponse createMoneyGame(Long memberId, Long roundId, GameType type, List<Long> participantIds,
			int penaltyCount) {
		Map<String, Object> body = Map.of("type", type, "penaltyKind", PenaltyKind.MONEY,
				"participantMemberIds", participantIds, "penaltyCount", penaltyCount);
		return ok(HttpMethod.POST, "/api/rounds/" + roundId + "/games", memberId, body, GameStateResponse.class);
	}

	/**
	 * 게임을 시작해 결과를 공개한다.
	 */
	public GameStateResponse startGame(Long memberId, Long gameId) {
		return ok(HttpMethod.POST, "/api/games/" + gameId + "/start", memberId, null, GameStateResponse.class);
	}

	/**
	 * 게임의 벌칙 금액 조건을 설정하거나 바꾼다. 종류에 안 맞는 값은 null로 보낸다.
	 */
	public void setRule(Long memberId, Long gameId, PenaltyMoneyType moneyType, Long targetExpenseId, Long amount,
			Integer ratioPct) {
		Map<String, Object> body = new HashMap<>();
		body.put("moneyType", moneyType);
		body.put("targetExpenseId", targetExpenseId);
		body.put("amount", amount);
		body.put("ratioPct", ratioPct);
		okData(HttpMethod.PUT, "/api/games/" + gameId + "/penalty-rule", memberId, body);
	}

	/**
	 * 모임의 정산을 조회한다.
	 */
	public SettlementResponse settlement(Long memberId, Long meetingId) {
		return ok(HttpMethod.GET, settlementUri(meetingId), memberId, null, SettlementResponse.class);
	}

	/**
	 * 확정된 리비전 목록을 조회한다.
	 */
	public List<SettlementRevisionResponse> revisions(Long memberId, Long meetingId) {
		JsonNode data = okData(HttpMethod.GET, settlementUri(meetingId) + "/revisions", memberId, null);
		return jsonMapper.treeToValue(data, jsonMapper.getTypeFactory()
				.constructCollectionType(List.class, SettlementRevisionResponse.class));
	}

	/**
	 * 정산을 확정(또는 재확정)한다. 성공해야 한다.
	 */
	public SettlementResponse confirm(Long memberId, Long meetingId, boolean acknowledgeWarnings) {
		return ok(HttpMethod.POST, settlementUri(meetingId) + "/confirm", memberId,
				Map.of("acknowledgeWarnings", acknowledgeWarnings), SettlementResponse.class);
	}

	/**
	 * 정산 확정을 요청하고 실패 응답을 돌려준다. 성공하면 테스트가 실패한다.
	 */
	public ApiError confirmExpectingError(Long memberId, Long meetingId, boolean acknowledgeWarnings) {
		Exchange exchange = exchange(HttpMethod.POST, settlementUri(meetingId) + "/confirm", memberId,
				Map.of("acknowledgeWarnings", acknowledgeWarnings));
		assertThat(exchange.body().path("success").asBoolean()).as("확정이 실패해야 한다").isFalse();
		JsonNode error = exchange.body().path("error");
		return new ApiError(exchange.status(), error.path("code").asString(), error.path("details"));
	}

	private static String settlementUri(Long meetingId) {
		return "/api/meetings/" + meetingId + "/settlement";
	}

	private <T> T ok(HttpMethod method, String uri, Long memberId, Object body, Class<T> type) {
		return jsonMapper.treeToValue(okData(method, uri, memberId, body), type);
	}

	/**
	 * API를 부르고 2xx·success=true를 확인한 뒤 data 노드를 돌려준다.
	 */
	private JsonNode okData(HttpMethod method, String uri, Long memberId, Object body) {
		Exchange exchange = exchange(method, uri, memberId, body);
		assertThat(exchange.status()).as("%s %s 응답: %s", method, uri, exchange.body()).isBetween(200, 299);
		assertThat(exchange.body().path("success").asBoolean()).isTrue();
		return exchange.body().path("data");
	}

	private Exchange exchange(HttpMethod method, String uri, Long memberId, Object body) {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, uri)
				.cookie(new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId)));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
		}
		try {
			MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
			return new Exchange(response.getStatus(),
					jsonMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(method + " " + uri + " 호출 실패", e);
		}
	}
}
