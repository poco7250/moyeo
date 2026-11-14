package com.poco7250.moyeo.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.service.GameDrawer;
import com.poco7250.moyeo.game.service.GameSeedGenerator;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.scenario.ScenarioApi.ApiError;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ExpenseDeleteResponse;
import com.poco7250.moyeo.settlement.dto.NetBalanceChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionPenaltyChargeResponse;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 4.5 계산 예시(예 1~6, 8케이스)와 S-8 FULL 2명 케이스를 실제 HTTP API로 구성해
 * 정산 조회·확정 응답이 PRD 표와 원 단위까지 같은지 확인한다(ROADMAP Task 041).
 * 공통 구성: 철수가 만든 그룹·모임(1차 밥, 2차 볼링), 2차 참석 4명(철수·민수·영희·지수), 2차 지출 1건(결제 철수).
 * 벌칙자는 추첨 seed를 고정해 PRD와 같은 사람으로 맞춘다. 실제 커밋을 보려고 테스트 트랜잭션은 쓰지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SettlementScenarioIntegrationTest {

	private static final long SEED_SEARCH_LIMIT = 10_000L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	GameSeedGenerator gameSeedGenerator;

	private ScenarioApi api;
	private Long chulsu;
	private Long minsu;
	private Long yeonghee;
	private Long jisu;
	private Long meetingId;
	private Long roundId;

	@BeforeEach
	void setUp() {
		api = new ScenarioApi(mockMvc, jwtTokenProvider, jsonMapper);
		chulsu = saveMember(980_001L, "철수");
		minsu = saveMember(980_002L, "민수");
		yeonghee = saveMember(980_003L, "영희");
		jisu = saveMember(980_004L, "지수");
		GroupCreateResponse group = api.createGroup(chulsu, "대학 동기");
		List<Long> everyone = List.of(chulsu, minsu, yeonghee, jisu);
		everyone.stream().skip(1).forEach(memberId -> api.join(memberId, group.inviteLink().token()));
		MeetingDetailResponse meeting = api.createMeeting(chulsu, group.group().id(), "연말 모임",
				RoundTemplate.MEAL_BOWLING);
		meetingId = meeting.id();
		roundId = meeting.rounds().stream().filter(round -> round.seq() == 2).findFirst().orElseThrow().id();
		everyone.forEach(memberId -> api.attend(memberId, meetingId, roundId, AttendanceStatus.ATTEND));
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("예 1: RATIO 30% — 민수 30,000 / 철수 23,200 / 영희·지수 23,400, 송금 3건 합 76,800, 경고 없음")
	void example1() {
		Long expenseId = bowling(100_000L);
		api.setRule(chulsu, minsuLadder(), PenaltyMoneyType.RATIO, expenseId, null, 30);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertExample1(response);
		assertThat(response.status()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(response.preview()).isTrue();
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 2: FIXED_AMOUNT 20,000 — 민수 20,000 / 철수 26,600 / 영희·지수 26,700")
	void example2() {
		Long expenseId = bowling(100_000L);
		api.setRule(chulsu, minsuLadder(), PenaltyMoneyType.FIXED_AMOUNT, expenseId, 20_000L, null);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertSettlement(response, 100_000L, amounts(26_600L, 20_000L, 26_700L, 26_700L),
				amounts(73_400L, -20_000L, -26_700L, -26_700L));
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 3: 고정 150,000은 잔액 100,000이 상한 — 민수 100,000, 경고 발생, 경고 확인 없이는 확정 409")
	void example3() {
		Long expenseId = bowling(100_000L);
		api.setRule(chulsu, minsuLadder(), PenaltyMoneyType.FIXED_AMOUNT, expenseId, 150_000L, null);

		SettlementResponse response = api.settlement(jisu, meetingId);
		ApiError rejected = api.confirmExpectingError(chulsu, meetingId, false);
		SettlementResponse confirmed = api.confirm(chulsu, meetingId, true);

		Map<Long, Long> shares = amounts(0L, 100_000L, 0L, 0L);
		Map<Long, Long> netBalances = amounts(100_000L, -100_000L, 0L, 0L);
		assertSettlement(response, 100_000L, shares, netBalances);
		assertThat(response.warnings()).extracting(warning -> warning.expenseId()).containsExactly(expenseId);
		assertThat(rejected.status()).isEqualTo(409);
		assertThat(rejected.code()).isEqualTo("WARNING_UNCONFIRMED");
		assertSettlement(confirmed, 100_000L, shares, netBalances);
		assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(confirmed.basisRevisionNo()).isEqualTo(1);
	}

	@Test
	@DisplayName("예 4-a: FULL은 올림 없이 잔액 그대로 — 민수 87,650, 나머지 0, 경고 없음")
	void example4a() {
		Long expenseId = bowling(87_650L);
		api.setRule(chulsu, minsuLadder(), PenaltyMoneyType.FULL, expenseId, null, null);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertSettlement(response, 87_650L, amounts(0L, 87_650L, 0L, 0L), amounts(87_650L, -87_650L, 0L, 0L));
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 4-b: 서로 다른 게임 RATIO 50% × 2는 공개 순서대로 — 민수 43,900 / 영희 43,750, 경고 없음")
	void example4b() {
		Long expenseId = bowling(87_650L);
		Long gameA = revealedGame(GameType.LADDER, List.of(chulsu, minsu), Set.of(minsu));
		Long gameB = revealedGame(GameType.LADDER, List.of(yeonghee, jisu), Set.of(yeonghee));
		// 조건 설정 순서와 상관없이 먼저 공개된 게임 A(민수)가 먼저 적용돼야 한다
		api.setRule(chulsu, gameB, PenaltyMoneyType.RATIO, expenseId, null, 50);
		api.setRule(chulsu, gameA, PenaltyMoneyType.RATIO, expenseId, null, 50);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertSettlement(response, 87_650L, amounts(0L, 43_900L, 43_750L, 0L),
				amounts(87_650L, -43_900L, -43_750L, 0L));
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 5-a: 한 규칙 RATIO 30% 벌칙자 2명 각자 적용 — 민수·영희 26,300 / 철수 17,450 / 지수 17,600")
	void example5a() {
		Long expenseId = bowling(87_650L);
		api.setRule(chulsu, minsuYeongheeLadder(), PenaltyMoneyType.RATIO, expenseId, null, 30);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertPenaltyOrder(response, minsu, yeonghee);
		assertSettlement(response, 87_650L, amounts(17_450L, 26_300L, 26_300L, 17_600L),
				amounts(70_200L, -26_300L, -26_300L, -17_600L));
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 5-b: 먼저 공개된 꽝뽑기 고정 50,000 뒤 잔액 균등 분할 — 지수 50,000 / 민수 18,900 / 영희 18,750, 경고 발생")
	void example5b() {
		Long expenseId = bowling(87_650L);
		Long draw = revealedGame(GameType.DRAW, List.of(chulsu, jisu), Set.of(jisu));
		Long ladder = minsuYeongheeLadder();
		api.setRule(chulsu, ladder, PenaltyMoneyType.RATIO, expenseId, null, 30);
		api.setRule(chulsu, draw, PenaltyMoneyType.FIXED_AMOUNT, expenseId, 50_000L, null);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertPenaltyOrder(response, jisu, minsu, yeonghee);
		assertSettlement(response, 87_650L, amounts(0L, 18_900L, 18_750L, 50_000L),
				amounts(87_650L, -18_900L, -18_750L, -50_000L));
		assertThat(response.warnings()).extracting(warning -> warning.expenseId()).containsExactly(expenseId);
	}

	@Test
	@DisplayName("S-8: 한 게임 FULL 벌칙자 2명은 균등 분할 — 민수 43,900 / 영희 43,750, 인원수 때문인 초과라 경고 없음")
	void fullTwoPenalized() {
		Long expenseId = bowling(87_650L);
		api.setRule(chulsu, minsuYeongheeLadder(), PenaltyMoneyType.FULL, expenseId, null, null);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertPenaltyOrder(response, minsu, yeonghee);
		assertSettlement(response, 87_650L, amounts(0L, 43_900L, 43_750L, 0L),
				amounts(87_650L, -43_900L, -43_750L, 0L));
		assertThat(response.warnings()).isEmpty();
	}

	@Test
	@DisplayName("예 6: 리비전 1 확정 → RATIO 50% 변경(REOPENED) → 재확정하면 리비전 2 송금 안내·변동액이 나오고 리비전 1은 그대로다")
	void example6() {
		Long expenseId = bowling(100_000L);
		Long gameId = minsuLadder();
		api.setRule(chulsu, gameId, PenaltyMoneyType.RATIO, expenseId, null, 30);
		SettlementResponse revision1 = api.confirm(chulsu, meetingId, false);

		api.setRule(chulsu, gameId, PenaltyMoneyType.RATIO, expenseId, null, 50);
		SettlementStatus statusAfterChange = api.settlement(minsu, meetingId).status();
		SettlementResponse revision2 = api.confirm(chulsu, meetingId, false);

		assertExample1(revision1);
		assertThat(revision1.revisionChange()).isNull();
		assertThat(statusAfterChange).isEqualTo(SettlementStatus.REOPENED);
		assertRevision2(revision2);
		assertRevisionHistory(revision1, revision2);
	}

	@Test
	@DisplayName("대상 지출을 삭제하면 벌칙이 ASSIGNED로 돌아가 확정이 409로 막히고, 조건을 다시 설정하면 확정된다")
	void targetExpenseDeleted() {
		Long expenseId = bowling(100_000L);
		Long gameId = minsuLadder();
		api.setRule(chulsu, gameId, PenaltyMoneyType.RATIO, expenseId, null, 30);

		ExpenseDeleteResponse deleted = api.deleteExpense(minsu, expenseId);
		SettlementPenaltyResponse penalty = api.settlement(jisu, meetingId).penalties().getFirst();
		ApiError rejected = api.confirmExpectingError(chulsu, meetingId, false);
		api.setRule(chulsu, gameId, PenaltyMoneyType.RATIO, bowling(100_000L), null, 30);
		SettlementResponse confirmed = api.confirm(chulsu, meetingId, false);

		assertThat(deleted.conditionResetGameIds()).containsExactly(gameId);
		assertThat(penalty.status()).isEqualTo(PenaltyStatus.ASSIGNED);
		assertThat(penalty.rule()).isNull();
		assertThat(rejected.status()).isEqualTo(409);
		assertThat(rejected.code()).isEqualTo("PENALTY_CONDITION_UNSET");
		assertThat(rejected.details().toString()).contains(String.valueOf(gameId));
		assertExample1(confirmed);
		assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(confirmed.basisRevisionNo()).isEqualTo(1);
	}

	/**
	 * 예 1(리비전 1)의 부담액·순잔액·송금 안내를 확인한다.
	 */
	private void assertExample1(SettlementResponse response) {
		assertSettlement(response, 100_000L, amounts(23_200L, 30_000L, 23_400L, 23_400L),
				amounts(76_800L, -30_000L, -23_400L, -23_400L));
	}

	/**
	 * 예 6 리비전 2의 부담액·순잔액·송금 안내와 리비전 1 대비 변동액을 확인한다.
	 */
	private void assertRevision2(SettlementResponse revision2) {
		assertSettlement(revision2, 100_000L, amounts(16_600L, 50_000L, 16_700L, 16_700L),
				amounts(83_400L, -50_000L, -16_700L, -16_700L));
		assertThat(revision2.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(revision2.basisRevisionNo()).isEqualTo(2);
		RevisionChangeResponse change = revision2.revisionChange();
		assertThat(change.previousRevisionNo()).isEqualTo(1);
		assertThat(change.notice()).isEqualTo(RevisionChangeResponse.NOTICE);
		assertThat(change.changes()).containsExactlyInAnyOrder(
				new NetBalanceChangeResponse(chulsu, 76_800L, 83_400L, 6_600L),
				new NetBalanceChangeResponse(minsu, -30_000L, -50_000L, -20_000L),
				new NetBalanceChangeResponse(yeonghee, -23_400L, -16_700L, 6_700L),
				new NetBalanceChangeResponse(jisu, -23_400L, -16_700L, 6_700L));
		assertThat(change.changes().stream().mapToLong(NetBalanceChangeResponse::delta).sum()).isZero();
	}

	/**
	 * 리비전 목록에서 리비전 1이 확정 당시 값 그대로이고 리비전 2 스냅샷이 저장됐는지 확인한다.
	 */
	private void assertRevisionHistory(SettlementResponse revision1, SettlementResponse revision2) {
		List<SettlementRevisionResponse> revisions = api.revisions(jisu, meetingId);
		assertThat(revisions).extracting(SettlementRevisionResponse::revisionNo).containsExactly(1, 2);
		SettlementRevisionResponse first = revisions.get(0);
		SettlementRevisionResponse second = revisions.get(1);
		assertThat(first.members()).containsExactlyInAnyOrderElementsOf(revision1.members());
		assertThat(first.transfers()).containsExactlyInAnyOrderElementsOf(revision1.transfers()).hasSize(3);
		assertThat(first.changes()).isNull();
		assertThat(first.penaltyCharges()).extracting(RevisionPenaltyChargeResponse::memberId,
				RevisionPenaltyChargeResponse::amount, RevisionPenaltyChargeResponse::ratioPct)
				.containsExactly(tuple(minsu, 30_000L, 30));
		assertThat(second.members()).containsExactlyInAnyOrderElementsOf(revision2.members());
		assertThat(second.transfers()).containsExactlyInAnyOrderElementsOf(revision2.transfers());
		assertThat(second.changes()).containsExactlyInAnyOrderElementsOf(revision2.revisionChange().changes());
		assertThat(second.penaltyCharges()).extracting(RevisionPenaltyChargeResponse::memberId,
				RevisionPenaltyChargeResponse::amount, RevisionPenaltyChargeResponse::ratioPct)
				.containsExactly(tuple(minsu, 50_000L, 50));
	}

	/**
	 * 부담액·순잔액이 기대값과 같고, 부담액 합 = 지출 합계, 순잔액 합 = 0, 송금 안내가 "낼 사람 → 철수" 전체인지 확인한다.
	 * 모든 예시에서 받을 사람은 결제자 철수 한 명이다.
	 */
	private void assertSettlement(SettlementResponse response, long expenseTotal, Map<Long, Long> shares,
			Map<Long, Long> netBalances) {
		assertThat(byMember(response, SettlementMemberResponse::shareAmount)).isEqualTo(shares);
		assertThat(byMember(response, SettlementMemberResponse::netBalance)).isEqualTo(netBalances);
		assertThat(response.members().stream().mapToLong(SettlementMemberResponse::shareAmount).sum())
				.isEqualTo(expenseTotal);
		assertThat(response.members().stream().mapToLong(SettlementMemberResponse::netBalance).sum()).isZero();
		List<SettlementTransferResponse> expectedTransfers = netBalances.entrySet().stream()
				.filter(entry -> entry.getValue() < 0)
				.map(entry -> new SettlementTransferResponse(entry.getKey(), chulsu, -entry.getValue()))
				.toList();
		assertThat(response.transfers()).containsExactlyInAnyOrderElementsOf(expectedTransfers);
		assertThat(response.transfers().stream().mapToLong(SettlementTransferResponse::amount).sum())
				.isEqualTo(netBalances.get(chulsu));
	}

	/**
	 * 벌칙 반영 내역이 적용 순서(게임 공개 순, 같은 게임은 Penalty.id 순)대로 이 회원들인지 확인한다.
	 */
	private void assertPenaltyOrder(SettlementResponse response, Long... memberIds) {
		assertThat(response.penalties()).extracting(SettlementPenaltyResponse::memberId).containsExactly(memberIds);
	}

	private Map<Long, Long> byMember(SettlementResponse response, ToLongFunction<SettlementMemberResponse> amount) {
		return response.members().stream()
				.collect(Collectors.toMap(SettlementMemberResponse::memberId, amount::applyAsLong));
	}

	/**
	 * 철수·민수·영희·지수 순서의 금액을 회원 id별 Map으로 만든다.
	 */
	private Map<Long, Long> amounts(long chulsuAmount, long minsuAmount, long yeongheeAmount, long jisuAmount) {
		Map<Long, Long> amounts = new LinkedHashMap<>();
		amounts.put(chulsu, chulsuAmount);
		amounts.put(minsu, minsuAmount);
		amounts.put(yeonghee, yeongheeAmount);
		amounts.put(jisu, jisuAmount);
		return amounts;
	}

	/**
	 * 2차에 철수가 결제한 "볼링" 지출을 등록하고 id를 돌려준다.
	 */
	private Long bowling(long amount) {
		return api.addExpense(chulsu, roundId, amount, chulsu, "볼링").id();
	}

	/**
	 * 민수·영희 사다리에서 민수가 벌칙자로 공개된 게임.
	 */
	private Long minsuLadder() {
		return revealedGame(GameType.LADDER, List.of(minsu, yeonghee), Set.of(minsu));
	}

	/**
	 * 철수·민수·영희 사다리에서 민수·영희 2명이 벌칙자로 공개된 게임.
	 */
	private Long minsuYeongheeLadder() {
		return revealedGame(GameType.LADDER, List.of(chulsu, minsu, yeonghee), Set.of(minsu, yeonghee));
	}

	/**
	 * 호스트가 금액형 게임을 만들어 시작하고, 원하는 회원이 벌칙자로 공개됐는지 확인한 뒤 게임 id를 돌려준다.
	 */
	private Long revealedGame(GameType type, List<Long> participantIds, Set<Long> penalizedIds) {
		given(gameSeedGenerator.next()).willReturn(seedFor(participantIds, penalizedIds));
		Long gameId = api.createMoneyGame(chulsu, roundId, type, participantIds, penalizedIds.size()).gameId();
		GameStateResponse revealed = api.startGame(chulsu, gameId);
		assertThat(revealed.result().penaltyMemberIds()).containsExactlyInAnyOrderElementsOf(penalizedIds);
		return gameId;
	}

	/**
	 * 추첨이 원하는 벌칙자를 뽑는 seed를 찾는다. 추첨은 seed만으로 결정되는 순수 함수다.
	 */
	private static long seedFor(List<Long> participantIds, Set<Long> penalizedIds) {
		return LongStream.range(0, SEED_SEARCH_LIMIT)
				.filter(seed -> GameDrawer.draw(participantIds, penalizedIds.size(), seed).equals(penalizedIds))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("원하는 벌칙자가 나오는 seed를 찾지 못했다"));
	}

	private Long saveMember(long kakaoId, String nickname) {
		return memberRepository.save(Member.ofKakao(kakaoId, nickname, null, null)).getId();
	}
}
