package com.poco7250.moyeo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.boardgame.dto.BoardGameRankRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordRequest;
import com.poco7250.moyeo.boardgame.service.BoardGameRecordService;
import com.poco7250.moyeo.e2e.E2eClient.Exchange;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.domain.InviteLink;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.group.repository.InviteLinkRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingDateCandidate;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingDateCandidateRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.FavoritePlace;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.repository.FavoritePlaceRepository;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공개 경로·인가 점검표(Task 043). 모든 API를 접근 등급으로 분류해 두고 등급별로 거부 응답을 확인한다.
 * 점검표에 없는 API가 생기면 completeness 테스트가 실패하므로 새 API는 여기 분류를 추가해야 한다.
 * 거부만 확인하므로 데이터가 바뀌지 않아 픽스처는 클래스당 한 번 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMatrixTest {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
	private static final String ROUNDS_BODY = "{\"rounds\":[{\"roundId\":{roundId},\"status\":\"ATTEND\"}]}";
	private static final String BOARD_GAME_BODY =
			"{\"gameName\":\"점검\",\"ranks\":[{\"memberId\":{hostId},\"rank\":1},{\"memberId\":{memberId},\"rank\":2}],"
					+ "\"loserMemberId\":{memberId}}";
	private static final String EXPENSE_BODY = "{\"amount\":1000,\"payerId\":{hostId},\"memo\":\"점검\"}";
	private static final String PUSH_SUBSCRIBE_BODY = "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/check\","
			+ "\"keys\":{\"p256dh\":\"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4\","
			+ "\"auth\":\"BTBZMqHH6r4Tts7J_aSIgg\"}}";
	// 이 표시가 body면 JSON 대신 영수증 사진 multipart 파트를 보낸다
	private static final String RECEIPT_FILE = "<receipt-file>";
	private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

	/** 접근 등급 */
	enum Access {
		/** 화이트리스트 공개 경로(SecurityConfig) */
		PUBLIC,
		/** 로그인만 필요(특정 그룹 리소스가 아님) */
		SELF,
		/** 그룹 멤버만(비멤버 403 NOT_GROUP_MEMBER) */
		MEMBER,
		/** 모임 호스트 권한자(호스트·방장, 정산 API는 총무도)만(일반 멤버 403 NOT_MEETING_HOST) */
		HOST,
		/** 게임 진행자 또는 호스트만(그 외 멤버 403 NOT_GAME_OPERATOR) */
		OPERATOR,
		/** 보드게임 기록한 사람 또는 호스트만(그 외 멤버 403 NOT_BOARD_GAME_RECORDER) */
		RECORDER,
		/** 그룹 방장만(그 외 멤버 403 NOT_GROUP_OWNER, G-4) */
		OWNER
	}

	/** 점검표 한 줄. path·body의 {이름}은 픽스처 값으로 바꿔 부른다 */
	record Endpoint(HttpMethod method, String path, Access access, String body) {

		@Override
		public String toString() {
			return method + " " + path;
		}
	}

	private static final List<Endpoint> ENDPOINTS = List.of(
			// 공개
			endpoint(HttpMethod.GET, "/api/auth/kakao/login", Access.PUBLIC),
			endpoint(HttpMethod.GET, "/api/auth/kakao/callback", Access.PUBLIC),
			endpoint(HttpMethod.POST, "/api/auth/refresh", Access.PUBLIC),
			endpoint(HttpMethod.POST, "/api/auth/logout", Access.PUBLIC),
			endpoint(HttpMethod.GET, "/api/invites/{token}", Access.PUBLIC),
			endpoint(HttpMethod.GET, "/share/meetings/{shareToken}", Access.PUBLIC),
			endpoint(HttpMethod.GET, "/share/invites/{token}", Access.PUBLIC),
			// 로그인만
			endpoint(HttpMethod.GET, "/api/members/me", Access.SELF),
			endpoint(HttpMethod.GET, "/api/push/vapid-public-key", Access.SELF),
			new Endpoint(HttpMethod.POST, "/api/members/me/push-subscriptions", Access.SELF, PUSH_SUBSCRIBE_BODY),
			new Endpoint(HttpMethod.DELETE, "/api/members/me/push-subscriptions", Access.SELF,
					"{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/check\"}"),
			endpoint(HttpMethod.GET, "/api/members/me/push-preferences", Access.SELF),
			new Endpoint(HttpMethod.PATCH, "/api/members/me/push-preferences", Access.SELF,
					"{\"enabled\":{\"MEETING_CREATED\":false}}"),
			new Endpoint(HttpMethod.POST, "/api/groups", Access.SELF, "{\"name\":\"새 그룹\"}"),
			endpoint(HttpMethod.GET, "/api/groups", Access.SELF),
			endpoint(HttpMethod.POST, "/api/invites/{token}/join", Access.SELF),
			endpoint(HttpMethod.GET, "/api/places/search", Access.SELF),
			// 그룹
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/members", Access.MEMBER),
			new Endpoint(HttpMethod.PUT, "/api/groups/{groupId}/members/{memberId}/role", Access.OWNER,
					"{\"role\":\"TREASURER\"}"),
			new Endpoint(HttpMethod.POST, "/api/groups/{groupId}/owner-transfer", Access.OWNER,
					"{\"memberId\":{memberId}}"),
			new Endpoint(HttpMethod.PATCH, "/api/groups/{groupId}/members/me", Access.MEMBER,
					"{\"nickname\":\"점검\"}"),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/members/me/payment", Access.MEMBER),
			new Endpoint(HttpMethod.PUT, "/api/groups/{groupId}/members/me/payment", Access.MEMBER,
					"{\"tossLink\":\"https://toss.me/check\"}"),
			endpoint(HttpMethod.DELETE, "/api/groups/{groupId}/members/me/payment", Access.MEMBER),
			endpoint(HttpMethod.POST, "/api/groups/{groupId}/invite-links", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/groups/{groupId}/meetings", Access.MEMBER,
					"{\"title\":\"점검 모임\",\"startAt\":\"2099-12-20T18:00:00\",\"template\":\"MEAL_BOWLING\"}"),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/meetings", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/history", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/hall-of-fame", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/year-review?year=2026", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/favorite-places", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/groups/{groupId}/favorite-places", Access.MEMBER,
					"{\"label\":\"볼링장\"}"),
			endpoint(HttpMethod.DELETE, "/api/groups/{groupId}/favorite-places/{favoriteId}", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/groups/{groupId}/recent-places", Access.MEMBER),
			// 모임·참석
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}", Access.MEMBER),
			new Endpoint(HttpMethod.PUT, "/api/meetings/{meetingId}/host", Access.HOST, "{\"memberId\":{memberId}}"),
			new Endpoint(HttpMethod.PUT, "/api/meetings/{meetingId}/attendances/me", Access.MEMBER, ROUNDS_BODY),
			new Endpoint(HttpMethod.PUT, "/api/meetings/{meetingId}/attendances/{memberId}", Access.HOST,
					ROUNDS_BODY),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/attendance-board", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/summary-card", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/attendance-reminder", Access.MEMBER),
			endpoint(HttpMethod.POST, "/api/meetings/{meetingId}/attendance-reminder/push", Access.MEMBER),
			// 당일 체크인·실제 참석자 확정
			endpoint(HttpMethod.PUT, "/api/rounds/{roundId}/check-ins/me", Access.MEMBER),
			endpoint(HttpMethod.DELETE, "/api/rounds/{roundId}/check-ins/me", Access.MEMBER),
			// 대상이 호출자 본인이면 본인 체크인으로 처리되므로, 호스트를 대상으로 비호스트 거부를 본다
			endpoint(HttpMethod.PUT, "/api/rounds/{roundId}/check-ins/{hostId}", Access.HOST),
			endpoint(HttpMethod.DELETE, "/api/rounds/{roundId}/check-ins/{hostId}", Access.HOST),
			endpoint(HttpMethod.POST, "/api/rounds/{roundId}/attendance/confirm", Access.HOST),
			// 날짜 후보 투표
			new Endpoint(HttpMethod.POST, "/api/meetings/{meetingId}/date-candidates", Access.HOST,
					"{\"startAt\":\"2099-12-21T18:00:00\"}"),
			new Endpoint(HttpMethod.PUT, "/api/meetings/{meetingId}/date-votes/me", Access.MEMBER,
					"{\"availableCandidateIds\":[]}"),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/date-votes", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/meetings/{meetingId}/date/confirm", Access.HOST,
					"{\"candidateId\":{dateCandidateId}}"),
			// 장소
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/candidates", Access.MEMBER, "{\"label\":\"볼링장\"}"),
			endpoint(HttpMethod.PUT, "/api/rounds/{roundId}/candidates/{candidateId}/vote", Access.MEMBER),
			endpoint(HttpMethod.DELETE, "/api/rounds/{roundId}/candidates/{candidateId}/vote", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/rounds/{roundId}/votes", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/place/confirm-direct", Access.HOST,
					"{\"label\":\"고깃집\"}"),
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/place/confirm", Access.HOST,
					"{\"candidateId\":{candidateId}}"),
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/place/voting", Access.HOST,
					"{\"placeVoteDeadline\":\"2099-12-19T18:00:00\"}"),
			endpoint(HttpMethod.POST, "/api/rounds/{roundId}/place/roulette", Access.HOST),
			endpoint(HttpMethod.GET, "/api/rounds/{roundId}/place/roulettes", Access.MEMBER),
			// 지출·정산
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/expenses", Access.MEMBER, EXPENSE_BODY),
			new Endpoint(HttpMethod.PUT, "/api/expenses/{expenseId}", Access.MEMBER, EXPENSE_BODY),
			endpoint(HttpMethod.DELETE, "/api/expenses/{expenseId}", Access.MEMBER),
			new Endpoint(HttpMethod.PUT, "/api/expenses/{expenseId}/shares", Access.MEMBER, "{\"shares\":[]}"),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/expenses", Access.MEMBER),
			new Endpoint(HttpMethod.PUT, "/api/expenses/{expenseId}/receipt", Access.MEMBER, RECEIPT_FILE),
			endpoint(HttpMethod.DELETE, "/api/expenses/{expenseId}/receipt", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/expenses/{expenseId}/receipt", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/settlement", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/settlement/revisions", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/meetings/{meetingId}/settlement/confirm", Access.HOST,
					"{\"acknowledgeWarnings\":false}"),
			endpoint(HttpMethod.GET, "/api/meetings/{meetingId}/change-logs", Access.MEMBER),
			// 게임·벌칙
			endpoint(HttpMethod.GET, "/api/rounds/{roundId}/games", Access.MEMBER),
			endpoint(HttpMethod.GET, "/api/games/{gameId}/state", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/games", Access.MEMBER,
					"{\"type\":\"LADDER\",\"penaltyKind\":\"MONEY\",\"participantMemberIds\":[{hostId},{memberId}]}"),
			endpoint(HttpMethod.POST, "/api/games/{gameId}/start", Access.OPERATOR),
			endpoint(HttpMethod.POST, "/api/games/{gameId}/close", Access.OPERATOR),
			endpoint(HttpMethod.POST, "/api/games/{gameId}/cancel", Access.OPERATOR),
			endpoint(HttpMethod.POST, "/api/games/{gameId}/rerun", Access.OPERATOR),
			new Endpoint(HttpMethod.PUT, "/api/games/{gameId}/penalty-rule", Access.HOST,
					"{\"moneyType\":\"FULL\",\"targetExpenseId\":{expenseId}}"),
			endpoint(HttpMethod.DELETE, "/api/games/{gameId}/penalty-rule", Access.HOST),
			endpoint(HttpMethod.POST, "/api/games/{gameId}/penalty-waiver", Access.HOST),
			endpoint(HttpMethod.DELETE, "/api/games/{gameId}/penalty-waiver", Access.HOST),
			// 보드게임 기록
			endpoint(HttpMethod.GET, "/api/rounds/{roundId}/board-games", Access.MEMBER),
			new Endpoint(HttpMethod.POST, "/api/rounds/{roundId}/board-games", Access.MEMBER, BOARD_GAME_BODY),
			new Endpoint(HttpMethod.PUT, "/api/board-games/{boardGameId}", Access.RECORDER, BOARD_GAME_BODY),
			endpoint(HttpMethod.DELETE, "/api/board-games/{boardGameId}", Access.RECORDER));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	InviteLinkRepository inviteLinkRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MeetingDateCandidateRepository meetingDateCandidateRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	FavoritePlaceRepository favoritePlaceRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	GameLobbyService gameLobbyService;

	@Autowired
	BoardGameRecordService boardGameRecordService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private E2eClient client;
	private final Map<String, String> fixture = new HashMap<>();
	private Cookie host;
	private Cookie member;
	private Cookie outsider;

	/**
	 * 호스트(모임 생성자·게임 진행자) 철수, 일반 멤버 민수, 그룹 밖 회원 외부인과 그룹 리소스 한 벌을 만든다.
	 */
	@BeforeAll
	void setUpFixture() {
		client = new E2eClient(mockMvc, jsonMapper);
		Member hostMember = saveMember("철수");
		Member plainMember = saveMember("민수");
		host = session(hostMember);
		member = session(plainMember);
		outsider = session(saveMember("외부인"));
		Group group = groupRepository.save(Group.create("점검 그룹"));
		groupMemberRepository.saveAll(List.of(GroupMember.join(group, hostMember, "철수", GroupRole.OWNER),
				GroupMember.join(group, plainMember, "민수", GroupRole.MEMBER)));
		InviteLink invite = inviteLinkRepository.save(InviteLink.issue(group, "matrix-invite-token",
				LocalDateTime.of(2099, 12, 31, 0, 0)));
		Meeting meeting = meetingRepository.save(Meeting.create(group, "점검 모임",
				LocalDateTime.of(2099, 12, 20, 18, 0), null, null, hostMember.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		Round round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		attendanceRepository.saveAll(List.of(attend(round, hostMember), attend(round, plainMember)));
		putIds(hostMember, plainMember, group, invite, meeting, round);
	}

	@AfterAll
	void tearDownFixture() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("점검표가 실제 등록된 API와 정확히 같다(분류 안 된 API가 생기면 실패)")
	void matrixCoversEveryEndpoint() {
		Set<String> registered = handlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(AuthorizationMatrixTest::signatures)
				.filter(signature -> !signature.contains(" /error"))
				.collect(Collectors.toSet());
		Set<String> listed = ENDPOINTS.stream()
				.map(e -> e.method() + " " + normalize(e.path()))
				.collect(Collectors.toSet());

		assertThat(listed).hasSameSizeAs(ENDPOINTS);
		assertThat(registered).containsExactlyInAnyOrderElementsOf(listed);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("nonPublicEndpoints")
	@DisplayName("공개 경로가 아닌 API는 쿠키 없이 부르면 401 UNAUTHORIZED다")
	void anonymousRejected(Endpoint endpoint) {
		Exchange response = call(endpoint, null);

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.errorCode()).isEqualTo("UNAUTHORIZED");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("publicEndpoints")
	@DisplayName("공개 경로는 쿠키 없이도 인증 필터에 막히지 않는다")
	void publicAllowsAnonymous(Endpoint endpoint) {
		Exchange response = call(endpoint, null);

		assertThat(response.errorCode()).isNotEqualTo("UNAUTHORIZED");
		assertThat(response.status()).isNotEqualTo(403);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("groupResourceEndpoints")
	@DisplayName("그룹 리소스 API는 그룹 밖 회원이면 403 NOT_GROUP_MEMBER다")
	void outsiderRejected(Endpoint endpoint) {
		Exchange response = call(endpoint, outsider);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.errorCode()).isEqualTo("NOT_GROUP_MEMBER");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("hostEndpoints")
	@DisplayName("호스트 전용 API는 호스트가 아닌 멤버면 403 NOT_MEETING_HOST다")
	void nonHostRejected(Endpoint endpoint) {
		Exchange response = call(endpoint, member);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.errorCode()).isEqualTo("NOT_MEETING_HOST");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("recorderEndpoints")
	@DisplayName("보드게임 기록 수정·삭제는 기록한 사람도 호스트도 아닌 멤버면 403 NOT_BOARD_GAME_RECORDER다")
	void nonRecorderRejected(Endpoint endpoint) {
		Exchange response = call(endpoint, member);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.errorCode()).isEqualTo("NOT_BOARD_GAME_RECORDER");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("ownerEndpoints")
	@DisplayName("역할 변경·방장 양도는 방장이 아닌 멤버면 403 NOT_GROUP_OWNER다")
	void nonOwnerRejected(Endpoint endpoint) {
		Exchange response = call(endpoint, member);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.errorCode()).isEqualTo("NOT_GROUP_OWNER");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("operatorEndpoints")
	@DisplayName("진행자 전용 API는 진행자도 호스트도 아닌 멤버면 403 NOT_GAME_OPERATOR다")
	void nonOperatorRejected(Endpoint endpoint) {
		Exchange response = call(endpoint, member);

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.errorCode()).isEqualTo("NOT_GAME_OPERATOR");
	}

	Stream<Endpoint> publicEndpoints() {
		return ENDPOINTS.stream().filter(e -> e.access() == Access.PUBLIC);
	}

	Stream<Endpoint> nonPublicEndpoints() {
		return ENDPOINTS.stream().filter(e -> e.access() != Access.PUBLIC);
	}

	Stream<Endpoint> groupResourceEndpoints() {
		Set<Access> groupResources = Set.of(Access.MEMBER, Access.HOST, Access.OPERATOR, Access.RECORDER,
				Access.OWNER);
		return ENDPOINTS.stream().filter(e -> groupResources.contains(e.access()));
	}

	Stream<Endpoint> hostEndpoints() {
		return ENDPOINTS.stream().filter(e -> e.access() == Access.HOST);
	}

	Stream<Endpoint> recorderEndpoints() {
		return ENDPOINTS.stream().filter(e -> e.access() == Access.RECORDER);
	}

	Stream<Endpoint> ownerEndpoints() {
		return ENDPOINTS.stream().filter(e -> e.access() == Access.OWNER);
	}

	Stream<Endpoint> operatorEndpoints() {
		return ENDPOINTS.stream().filter(e -> e.access() == Access.OPERATOR);
	}

	private Exchange call(Endpoint endpoint, Cookie session) {
		Object body = RECEIPT_FILE.equals(endpoint.body())
				? new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES)
				: endpoint.body() == null ? null : resolve(endpoint.body());
		return client.call(endpoint.method(), resolve(endpoint.path()), session, body);
	}

	/**
	 * 자리표시자를 픽스처 값으로 바꾼다. 모르는 이름이면 바로 실패한다(점검표 오타 방지).
	 */
	private String resolve(String template) {
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder resolved = new StringBuilder();
		while (matcher.find()) {
			String value = fixture.get(matcher.group(1));
			assertThat(value).as("픽스처에 %s가 없다", matcher.group(1)).isNotNull();
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

	/**
	 * 픽스처 id를 자리표시자 이름으로 등록한다. 후보·지출·게임은 차수가 생긴 뒤 만든다.
	 */
	private void putIds(Member hostMember, Member plainMember, Group group, InviteLink invite, Meeting meeting,
			Round round) {
		fixture.put("hostId", hostMember.getId().toString());
		fixture.put("memberId", plainMember.getId().toString());
		fixture.put("groupId", group.getId().toString());
		fixture.put("token", invite.getToken());
		fixture.put("meetingId", meeting.getId().toString());
		fixture.put("shareToken", meeting.getShareToken());
		fixture.put("roundId", round.getId().toString());
		fixture.put("dateCandidateId", meetingDateCandidateRepository.save(MeetingDateCandidate.propose(meeting,
				LocalDateTime.of(2099, 12, 21, 18, 0))).getId().toString());
		fixture.put("favoriteId", favoritePlaceRepository.save(FavoritePlace.save(group,
				Place.of("단골 볼링장", null, null), plainMember)).getId().toString());
		fixture.put("candidateId", placeCandidateRepository.save(PlaceCandidate.propose(round,
				Place.of("볼링장", null, null), plainMember)).getId().toString());
		fixture.put("expenseId", expenseRepository.save(Expense.create(round, hostMember, 50_000L, "볼링"))
				.getId().toString());
		fixture.put("gameId", gameLobbyService.create(round.getId(), hostMember.getId(), new GameCreateRequest(
				GameType.LADDER, PenaltyKind.MONEY, null, List.of(hostMember.getId(), plainMember.getId()), null))
				.gameId().toString());
		// 호스트가 기록해 둬 일반 멤버는 기록한 사람도 호스트도 아니다
		fixture.put("boardGameId", boardGameRecordService.create(round.getId(), hostMember.getId(),
				new BoardGameRecordRequest("점검", List.of(new BoardGameRankRequest(hostMember.getId(), 1),
						new BoardGameRankRequest(plainMember.getId(), 2)), plainMember.getId())).id().toString());
	}

	private static Stream<String> signatures(RequestMappingInfo info) {
		return info.getMethodsCondition().getMethods().stream()
				.flatMap(method -> info.getPathPatternsCondition().getPatternValues().stream()
						.map(path -> method.name() + " " + normalize(path)));
	}

	/** 경로 변수 이름은 컨트롤러마다 달라서 {}로 통일해 비교한다 */
	// 필수 쿼리 파라미터(예: year)는 매핑 비교에서 뗀다
	private static String normalize(String path) {
		return PLACEHOLDER.matcher(path.split("\\?", 2)[0]).replaceAll("{}");
	}

	private static Endpoint endpoint(HttpMethod method, String path, Access access) {
		return new Endpoint(method, path, access, null);
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private Cookie session(Member who) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(who.getId()));
	}

	private static Attendance attend(Round round, Member who) {
		return Attendance.respond(round, who, AttendanceStatus.ATTEND, LocalDateTime.of(2099, 12, 1, 12, 0));
	}
}
