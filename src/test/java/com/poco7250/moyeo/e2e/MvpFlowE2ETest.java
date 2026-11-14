package com.poco7250.moyeo.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.auth.client.KakaoOAuthClient;
import com.poco7250.moyeo.auth.dto.KakaoUserInfo;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.service.GameDrawer;
import com.poco7250.moyeo.game.service.GameSeedGenerator;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.group.dto.InvitePreviewResponse;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.MeetingDateStatus;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import com.poco7250.moyeo.meeting.dto.DateCandidateVotes;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.dto.RoundResponse;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.NetBalanceChangeResponse;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 3장 핵심 사용자 흐름을 새 그룹에서 HTTP API로 끝까지 수행한다(Task 043).
 * 카카오 OAuth 클라이언트만 스텁으로 바꾸고, 로그인·초대 복귀·가입부터 재확정까지 실제 엔드포인트를 거친다.
 * 벌칙자는 추첨 seed를 고정해 민수로 맞춘다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MvpFlowE2ETest {

	private static final long SEED_SEARCH_LIMIT = 10_000L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	KakaoOAuthClient kakaoOAuthClient;

	@MockitoBean
	GameSeedGenerator gameSeedGenerator;

	private E2eClient client;
	private final Map<String, Cookie> sessions = new HashMap<>();
	private final Map<String, Long> ids = new HashMap<>();

	@BeforeEach
	void setUp() {
		client = new E2eClient(mockMvc, jsonMapper);
		given(kakaoOAuthClient.buildAuthorizeUrl(anyString())).willReturn("https://kauth.kakao.com/oauth/authorize");
		stubKakaoUser("철수", 970_001L);
		stubKakaoUser("민수", 970_002L);
		stubKakaoUser("영희", 970_003L);
		stubKakaoUser("지수", 970_004L);
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("그룹 생성 → 초대 → 가입 → 모임·차수 → 날짜 투표·확정 → 참석 → 장소 → 게임 → 지출 → 조건 → 확정 → 수정 → 재확정")
	void coreFlow() {
		String inviteToken = hostCreatesGroup();
		guestsJoinByInvite(inviteToken);
		MeetingDetailResponse meeting = createMeeting();
		decideDate(meeting.id());
		Long meal = roundOf(meeting, 1);
		Long bowling = roundOf(meeting, 2);
		decidePlaces(meeting.id(), meal, bowling);
		List.of("철수", "민수", "영희", "지수").forEach(name -> attendBoth(name, meeting.id(), meal, bowling));
		Long gameId = minsuLosesLadder(bowling);
		Long mealExpense = addExpense(meal, 80_000L, "밥");
		Long bowlingExpense = addExpense(bowling, 100_000L, "볼링");
		setRatioRule(gameId, bowlingExpense, 30);

		SettlementResponse revision1 = confirm(meeting.id());
		assertRevision1(revision1);

		editExpenseAsJisu(mealExpense, 120_000L);
		assertReopenedWithChangeLog(meeting.id(), mealExpense);

		SettlementResponse revision2 = confirm(meeting.id());
		assertRevision2(revision2);
		assertRevisionHistory(meeting.id(), revision1);
		assertTimestampsStored(meeting.id());
	}

	// ---- 흐름 단계 ----

	private String hostCreatesGroup() {
		sessions.put("철수", client.login("code-철수", "/"));
		ids.put("철수", me("철수"));
		GroupCreateResponse group = client.ok(HttpMethod.POST, "/api/groups", sessions.get("철수"),
				Map.of("name", "대학 동기"), GroupCreateResponse.class);
		ids.put("group", group.group().id());
		return group.inviteLink().token();
	}

	/**
	 * 게스트는 비로그인으로 초대 미리보기를 보고, 그 링크로 돌아오게 로그인한 뒤 자동 가입한다.
	 */
	private void guestsJoinByInvite(String token) {
		InvitePreviewResponse preview = client.ok(HttpMethod.GET, "/api/invites/" + token, null, null,
				InvitePreviewResponse.class);
		assertThat(preview.groupName()).isEqualTo("대학 동기");
		for (String name : List.of("민수", "영희", "지수")) {
			sessions.put(name, client.login("code-" + name, "/invites/" + token));
			ids.put(name, me(name));
			JsonNode joined = client.okData(HttpMethod.POST, "/api/invites/" + token + "/join", sessions.get(name),
					null);
			assertThat(joined.path("joined").asBoolean()).isTrue();
		}
		JsonNode members = client.okData(HttpMethod.GET, "/api/groups/" + ids.get("group") + "/members",
				sessions.get("지수"), null);
		assertThat(members.size()).isEqualTo(4);
	}

	/**
	 * 날짜가 미정이라 후보 두 개로 날짜 투표 모임을 만든다(PRD M-5).
	 */
	private MeetingDetailResponse createMeeting() {
		Map<String, Object> body = Map.of("title", "연말 모임", "template", RoundTemplate.MEAL_BOWLING,
				"dateCandidates", List.of("2099-12-19T18:00:00", "2099-12-20T18:00:00"));
		MeetingDetailResponse meeting = client.ok(HttpMethod.POST, "/api/groups/" + ids.get("group") + "/meetings",
				sessions.get("철수"), body, MeetingDetailResponse.class);
		assertThat(meeting.dateStatus()).isEqualTo(MeetingDateStatus.VOTING);
		assertThat(meeting.startAt()).isNull();
		assertThat(meeting.rounds()).extracting(RoundResponse::placeStatus)
				.containsExactly(PlaceStatus.UNDECIDED, PlaceStatus.UNDECIDED);
		return meeting;
	}

	/**
	 * 게스트는 되는 날짜를 고르고(지수는 19일이 안 됨), 호스트가 가장 많이 되는 20일로 확정한다.
	 */
	private void decideDate(Long meetingId) {
		String votesUri = "/api/meetings/" + meetingId + "/date-votes";
		MeetingDateVotesResponse votes = client.ok(HttpMethod.GET, votesUri, sessions.get("민수"), null,
				MeetingDateVotesResponse.class);
		Long dec19 = votes.candidates().get(0).candidateId();
		Long dec20 = votes.candidates().get(1).candidateId();
		respondDates(meetingId, "민수", List.of(dec19, dec20));
		respondDates(meetingId, "영희", List.of(dec19, dec20));
		MeetingDateVotesResponse tally = respondDates(meetingId, "지수", List.of(dec20));
		assertThat(tally.candidates()).extracting(DateCandidateVotes::availableCount).containsExactly(2, 3);

		MeetingDetailResponse confirmed = client.ok(HttpMethod.POST, "/api/meetings/" + meetingId + "/date/confirm",
				sessions.get("철수"), Map.of("candidateId", dec20), MeetingDetailResponse.class);
		assertThat(confirmed.dateStatus()).isEqualTo(MeetingDateStatus.CONFIRMED);
		assertThat(confirmed.startAt()).isEqualTo(LocalDateTime.of(2099, 12, 20, 18, 0));
	}

	private MeetingDateVotesResponse respondDates(Long meetingId, String name, List<Long> availableIds) {
		return client.ok(HttpMethod.PUT, "/api/meetings/" + meetingId + "/date-votes/me", sessions.get(name),
				Map.of("availableCandidateIds", availableIds), MeetingDateVotesResponse.class);
	}

	/**
	 * 1차는 호스트가 바로 지정하고, 2차는 투표를 열어 게스트가 후보를 내고 투표한 뒤 호스트가 확정한다.
	 */
	private void decidePlaces(Long meetingId, Long meal, Long bowling) {
		client.ok(HttpMethod.POST, "/api/rounds/" + meal + "/place/confirm-direct", sessions.get("철수"),
				Map.of("label", "강남 고깃집"), RoundPlaceResponse.class);
		client.ok(HttpMethod.POST, "/api/rounds/" + bowling + "/place/voting", sessions.get("철수"),
				Map.of("placeVoteDeadline", "2099-12-19T18:00:00"), RoundPlaceResponse.class);
		Long laneA = addCandidate(bowling, "민수", "볼링장 A");
		addCandidate(bowling, "영희", "볼링장 B");
		List.of("민수", "영희", "지수").forEach(name -> client.okData(HttpMethod.PUT,
				"/api/rounds/" + bowling + "/candidates/" + laneA + "/vote", sessions.get(name), null));
		RoundPlaceResponse confirmed = client.ok(HttpMethod.POST, "/api/rounds/" + bowling + "/place/confirm",
				sessions.get("철수"), Map.of("candidateId", laneA), RoundPlaceResponse.class);
		assertThat(confirmed.confirmedCandidateId()).isEqualTo(laneA);

		MeetingDetailResponse detail = client.ok(HttpMethod.GET, "/api/meetings/" + meetingId, sessions.get("영희"),
				null, MeetingDetailResponse.class);
		assertThat(detail.rounds()).extracting(RoundResponse::placeStatus)
				.containsExactly(PlaceStatus.CONFIRMED, PlaceStatus.CONFIRMED);
	}

	private Long addCandidate(Long roundId, String name, String label) {
		return client.ok(HttpMethod.POST, "/api/rounds/" + roundId + "/candidates", sessions.get(name),
				Map.of("label", label), CandidateResponse.class).id();
	}

	private void attendBoth(String name, Long meetingId, Long meal, Long bowling) {
		Map<String, Object> body = Map.of("rounds", List.of(
				Map.of("roundId", meal, "status", AttendanceStatus.ATTEND),
				Map.of("roundId", bowling, "status", AttendanceStatus.ATTEND)));
		client.okData(HttpMethod.PUT, "/api/meetings/" + meetingId + "/attendances/me", sessions.get(name), body);
	}

	/**
	 * 2차에서 네 명이 금액형 사다리를 하고 민수가 벌칙자로 공개된다.
	 */
	private Long minsuLosesLadder(Long bowling) {
		List<Long> participants = List.of(ids.get("철수"), ids.get("민수"), ids.get("영희"), ids.get("지수"));
		given(gameSeedGenerator.next()).willReturn(seedFor(participants, Set.of(ids.get("민수"))));
		Map<String, Object> body = Map.of("type", GameType.LADDER, "penaltyKind", PenaltyKind.MONEY,
				"participantMemberIds", participants, "penaltyCount", 1);
		Long gameId = client.ok(HttpMethod.POST, "/api/rounds/" + bowling + "/games", sessions.get("민수"), body,
				GameStateResponse.class).gameId();
		GameStateResponse revealed = client.ok(HttpMethod.POST, "/api/games/" + gameId + "/start",
				sessions.get("민수"), null, GameStateResponse.class);
		assertThat(revealed.status()).isEqualTo(GameStatus.REVEALED);
		assertThat(revealed.result().penaltyMemberIds()).containsExactly(ids.get("민수"));
		return gameId;
	}

	private Long addExpense(Long roundId, long amount, String memo) {
		Map<String, Object> body = Map.of("amount", amount, "payerId", ids.get("철수"), "memo", memo);
		return client.ok(HttpMethod.POST, "/api/rounds/" + roundId + "/expenses", sessions.get("철수"), body,
				ExpenseResponse.class).id();
	}

	private void setRatioRule(Long gameId, Long expenseId, int ratioPct) {
		Map<String, Object> body = Map.of("moneyType", PenaltyMoneyType.RATIO, "targetExpenseId", expenseId,
				"ratioPct", ratioPct);
		client.okData(HttpMethod.PUT, "/api/games/" + gameId + "/penalty-rule", sessions.get("철수"), body);
	}

	private SettlementResponse confirm(Long meetingId) {
		return client.ok(HttpMethod.POST, "/api/meetings/" + meetingId + "/settlement/confirm", sessions.get("철수"),
				Map.of("acknowledgeWarnings", false), SettlementResponse.class);
	}

	private void editExpenseAsJisu(Long expenseId, long amount) {
		Map<String, Object> body = Map.of("amount", amount, "payerId", ids.get("철수"), "memo", "밥");
		client.okData(HttpMethod.PUT, "/api/expenses/" + expenseId, sessions.get("지수"), body);
	}

	// ---- 검증 ----

	/**
	 * 리비전 1: 밥 80,000은 4명 균등 20,000, 볼링 100,000은 민수 30% = 30,000, 나머지 70,000을 셋이 나눈다.
	 */
	private void assertRevision1(SettlementResponse response) {
		assertThat(response.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(response.basisRevisionNo()).isEqualTo(1);
		assertThat(response.members()).containsExactlyInAnyOrderElementsOf(members(
				43_200L, 136_800L, 50_000L, -50_000L, 43_400L, -43_400L, 43_400L, -43_400L));
		assertThat(response.transfers()).containsExactlyInAnyOrderElementsOf(transfersToHost(50_000L, 43_400L,
				43_400L));
		assertThat(response.revisionChange()).isNull();
	}

	private void assertReopenedWithChangeLog(Long meetingId, Long expenseId) {
		SettlementResponse reopened = client.ok(HttpMethod.GET, "/api/meetings/" + meetingId + "/settlement",
				sessions.get("영희"), null, SettlementResponse.class);
		assertThat(reopened.status()).isEqualTo(SettlementStatus.REOPENED);
		JsonNode logs = client.okData(HttpMethod.GET, "/api/meetings/" + meetingId + "/change-logs",
				sessions.get("영희"), null).path("items");
		assertThat(logs.valueStream().anyMatch(log -> "EXPENSE".equals(log.path("entityType").asString())
				&& "UPDATE".equals(log.path("changeType").asString())
				&& log.path("entityId").asLong() == expenseId
				&& log.path("actorId").asLong() == ids.get("지수"))).isTrue();
	}

	/**
	 * 리비전 2: 밥이 120,000이 되어 1인 30,000. 철수는 결제 +40,000·부담 +10,000이라 +30,000, 나머지는 −10,000.
	 */
	private void assertRevision2(SettlementResponse response) {
		assertThat(response.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(response.basisRevisionNo()).isEqualTo(2);
		assertThat(response.members()).containsExactlyInAnyOrderElementsOf(members(
				53_200L, 166_800L, 60_000L, -60_000L, 53_400L, -53_400L, 53_400L, -53_400L));
		assertThat(response.transfers()).containsExactlyInAnyOrderElementsOf(transfersToHost(60_000L, 53_400L,
				53_400L));
		assertThat(response.revisionChange().previousRevisionNo()).isEqualTo(1);
		assertThat(response.revisionChange().changes())
				.extracting(NetBalanceChangeResponse::memberId, NetBalanceChangeResponse::delta)
				.containsExactlyInAnyOrder(
						tuple(ids.get("철수"), 30_000L),
						tuple(ids.get("민수"), -10_000L),
						tuple(ids.get("영희"), -10_000L),
						tuple(ids.get("지수"), -10_000L));
		assertThat(response.revisionChange().shareText()).contains("리비전 2");
	}

	private void assertRevisionHistory(Long meetingId, SettlementResponse revision1) {
		JsonNode data = client.okData(HttpMethod.GET, "/api/meetings/" + meetingId + "/settlement/revisions",
				sessions.get("민수"), null);
		List<SettlementRevisionResponse> revisions = jsonMapper.treeToValue(data, jsonMapper.getTypeFactory()
				.constructCollectionType(List.class, SettlementRevisionResponse.class));
		assertThat(revisions).extracting(SettlementRevisionResponse::revisionNo).containsExactlyInAnyOrder(1, 2);
		SettlementRevisionResponse first = revisions.stream().filter(r -> r.revisionNo() == 1).findFirst()
				.orElseThrow();
		assertThat(first.members()).containsExactlyInAnyOrderElementsOf(revision1.members());
		assertThat(first.transfers()).containsExactlyInAnyOrderElementsOf(revision1.transfers());
	}

	/**
	 * 1장 지표 산출에 쓰는 시각 필드가 모두 저장됐는지 본다.
	 */
	private void assertTimestampsStored(Long meetingId) {
		String rounds = "(select id from meeting_round where meeting_id = " + meetingId + ")";
		assertThat(countNull("select count(*) from attendance where round_id in " + rounds, "responded_at"))
				.isEqualTo(new long[] {8, 0});
		assertThat(countNull("select count(*) from expense where round_id in " + rounds, "created_at"))
				.isEqualTo(new long[] {2, 0});
		assertThat(countNull("select count(*) from settlement_revision where settlement_id = "
				+ "(select id from settlement where meeting_id = " + meetingId + ")", "confirmed_at"))
				.isEqualTo(new long[] {2, 0});
	}

	/**
	 * 전체 행 수와 그중 column이 NULL인 행 수를 돌려준다.
	 */
	private long[] countNull(String countSql, String column) {
		Long total = jdbcTemplate.queryForObject(countSql, Long.class);
		Long nulls = jdbcTemplate.queryForObject(countSql + " and " + column + " is null", Long.class);
		return new long[] {total, nulls};
	}

	// ---- 도우미 ----

	private void stubKakaoUser(String name, long kakaoId) {
		given(kakaoOAuthClient.fetchUserInfo("code-" + name)).willReturn(new KakaoUserInfo(kakaoId, name, null, null));
	}

	private Long me(String name) {
		return client.ok(HttpMethod.GET, "/api/members/me", sessions.get(name), null, MemberResponse.class).id();
	}

	private static Long roundOf(MeetingDetailResponse meeting, int seq) {
		return meeting.rounds().stream().filter(round -> round.seq() == seq).findFirst().orElseThrow().id();
	}

	/** 철수·민수·영희·지수 순서로 (부담액, 순잔액) 쌍을 받는다 */
	private List<SettlementMemberResponse> members(long... shareAndNet) {
		List<String> order = List.of("철수", "민수", "영희", "지수");
		return LongStream.range(0, order.size()).mapToObj(i -> new SettlementMemberResponse(
				ids.get(order.get((int) i)), shareAndNet[(int) i * 2], shareAndNet[(int) i * 2 + 1])).toList();
	}

	/** 민수·영희·지수가 결제자 철수에게 보내는 송금 안내 */
	private List<SettlementTransferResponse> transfersToHost(long minsu, long yeonghee, long jisu) {
		Long host = ids.get("철수");
		return List.of(new SettlementTransferResponse(ids.get("민수"), host, minsu),
				new SettlementTransferResponse(ids.get("영희"), host, yeonghee),
				new SettlementTransferResponse(ids.get("지수"), host, jisu));
	}

	private static long seedFor(List<Long> participantIds, Set<Long> penalizedIds) {
		return LongStream.range(0, SEED_SEARCH_LIMIT)
				.filter(seed -> GameDrawer.draw(participantIds, penalizedIds.size(), seed).equals(penalizedIds))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("원하는 벌칙자가 나오는 seed를 찾지 못했다"));
	}
}
