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
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
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
 * PRD 4.5 금액형 게임 벌칙 계산 예시(예 1~6, 2026-11-24 개정)를 실제 HTTP API로 구성해
 * 정산 조회·확정 응답이 PRD 표와 원 단위까지 같은지 확인한다(ROADMAP Task 041 → 074).
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
	@DisplayName("예 1: 민수·영희 사다리 10,000원 민수 당첨 — 민수 35,000 / 영희 15,000 / 철수·지수 25,000, 경고 없음")
	void example1() {
		bowling(100_000L);
		Long gameId = revealedGame(GameType.LADDER, List.of(minsu, yeonghee), Set.of(minsu), 10_000L);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertExample1(response);
		assertThat(response.warnings()).isEmpty();
		assertThat(response.penalties()).singleElement().satisfies(penalty -> {
			assertThat(penalty.memberId()).isEqualTo(minsu);
			assertThat(penalty.game().gameId()).isEqualTo(gameId);
			assertThat(penalty.game().penaltyAmount()).isEqualTo(10_000L);
			assertThat(penalty.chargeAmount()).isEqualTo(10_000L);
			assertThat(penalty.creditedMemberIds()).containsExactly(yeonghee);
		});
	}

	@Test
	@DisplayName("예 2: 철수·민수·영희 사다리 10,000원 민수 당첨 — 민수 35,000 / 철수·영희 20,000 / 지수 25,000")
	void example2() {
		bowling(100_000L);
		revealedGame(GameType.LADDER, List.of(chulsu, minsu, yeonghee), Set.of(minsu), 10_000L);

		assertSettlement(api.settlement(jisu, meetingId), 100_000L, amounts(20_000L, 35_000L, 20_000L, 25_000L),
				amounts(80_000L, -35_000L, -20_000L, -25_000L));
	}

	@Test
	@DisplayName("예 3: 4명 사다리 10,000원 민수 당첨 — 3명이 3,333원씩, 남는 1원은 회원 id가 가장 작은 철수가 더 받는다")
	void example3() {
		bowling(100_000L);
		revealedGame(GameType.LADDER, List.of(chulsu, minsu, yeonghee, jisu), Set.of(minsu), 10_000L);

		assertSettlement(api.settlement(jisu, meetingId), 100_000L, amounts(21_666L, 35_000L, 21_667L, 21_667L),
				amounts(78_334L, -35_000L, -21_667L, -21_667L));
	}

	@Test
	@DisplayName("예 4: 철수·민수·영희 사다리 6,000원 민수·영희 2명 당첨 — 각자 6,000원, 철수가 12,000원을 받는다")
	void example4() {
		bowling(100_000L);
		revealedGame(GameType.LADDER, List.of(chulsu, minsu, yeonghee), Set.of(minsu, yeonghee), 6_000L);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertSettlement(response, 100_000L, amounts(13_000L, 31_000L, 31_000L, 25_000L),
				amounts(87_000L, -31_000L, -31_000L, -25_000L));
		assertThat(response.penalties()).extracting(SettlementPenaltyResponse::memberId)
				.containsExactlyInAnyOrder(minsu, yeonghee);
	}

	@Test
	@DisplayName("예 5: 면제하면 벌칙 금액이 빠져 4명 모두 25,000원이고 반영 내역에는 WAIVED로 남는다")
	void example5Waived() {
		bowling(100_000L);
		Long gameId = revealedGame(GameType.LADDER, List.of(minsu, yeonghee), Set.of(minsu), 10_000L);
		api.waive(chulsu, gameId);

		SettlementResponse response = api.settlement(jisu, meetingId);

		assertSettlement(response, 100_000L, amounts(25_000L, 25_000L, 25_000L, 25_000L),
				amounts(75_000L, -25_000L, -25_000L, -25_000L));
		assertThat(response.penalties()).singleElement().satisfies(penalty -> {
			assertThat(penalty.status()).isEqualTo(PenaltyStatus.WAIVED);
			assertThat(penalty.chargeAmount()).isNull();
		});
	}

	@Test
	@DisplayName("예 6: 예 1로 리비전 1 확정 → 면제(REOPENED) → 재확정하면 리비전 2 송금 안내·변동액이 나오고 리비전 1은 그대로다")
	void example6() {
		bowling(100_000L);
		Long gameId = revealedGame(GameType.LADDER, List.of(minsu, yeonghee), Set.of(minsu), 10_000L);
		SettlementResponse revision1 = api.confirm(chulsu, meetingId, false);

		api.waive(chulsu, gameId);
		SettlementStatus statusAfterChange = api.settlement(minsu, meetingId).status();
		SettlementResponse revision2 = api.confirm(chulsu, meetingId, false);

		assertExample1(revision1);
		assertThat(statusAfterChange).isEqualTo(SettlementStatus.REOPENED);
		assertSettlement(revision2, 100_000L, amounts(25_000L, 25_000L, 25_000L, 25_000L),
				amounts(75_000L, -25_000L, -25_000L, -25_000L));
		assertThat(revision2.basisRevisionNo()).isEqualTo(2);
		RevisionChangeResponse change = revision2.revisionChange();
		assertThat(change.previousRevisionNo()).isEqualTo(1);
		assertThat(change.notice()).isEqualTo(RevisionChangeResponse.NOTICE);
		assertThat(change.changes()).filteredOn(item -> item.delta() != 0).containsExactlyInAnyOrder(
				new NetBalanceChangeResponse(minsu, -35_000L, -25_000L, 10_000L),
				new NetBalanceChangeResponse(yeonghee, -15_000L, -25_000L, -10_000L));
		List<SettlementRevisionResponse> revisions = api.revisions(jisu, meetingId);
		assertThat(revisions).extracting(SettlementRevisionResponse::revisionNo).containsExactly(1, 2);
		assertThat(revisions.get(0).members()).containsExactlyInAnyOrderElementsOf(revision1.members());
		assertThat(revisions.get(0).penaltyCharges()).extracting(RevisionPenaltyChargeResponse::memberId,
				RevisionPenaltyChargeResponse::amount, RevisionPenaltyChargeResponse::fixedAmount)
				.containsExactly(tuple(minsu, 10_000L, 10_000L));
		assertThat(revisions.get(1).penaltyCharges()).isEmpty();
	}

	@Test
	@DisplayName("지출을 지워도 게임 벌칙은 남아, 지출 없이 민수가 영희에게 10,000원을 보내는 정산으로 확정된다")
	void expenseDeletedKeepsPenalty() {
		Long expenseId = bowling(100_000L);
		revealedGame(GameType.LADDER, List.of(minsu, yeonghee), Set.of(minsu), 10_000L);

		ExpenseDeleteResponse deleted = api.deleteExpense(minsu, expenseId);
		SettlementResponse confirmed = api.confirm(chulsu, meetingId, false);

		assertThat(deleted.expenseId()).isEqualTo(expenseId);
		assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(byMember(confirmed, SettlementMemberResponse::shareAmount))
				.containsEntry(minsu, 10_000L).containsEntry(yeonghee, -10_000L);
		assertThat(confirmed.transfers()).containsExactly(new SettlementTransferResponse(minsu, yeonghee, 10_000L));
	}

	/**
	 * 예 1(리비전 1)의 부담액·순잔액·송금 안내를 확인한다.
	 */
	private void assertExample1(SettlementResponse response) {
		assertSettlement(response, 100_000L, amounts(25_000L, 35_000L, 15_000L, 25_000L),
				amounts(75_000L, -35_000L, -15_000L, -25_000L));
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
	 * 호스트가 금액형 게임을 만들어 시작하고, 원하는 회원이 벌칙자로 공개됐는지 확인한 뒤 게임 id를 돌려준다.
	 */
	private Long revealedGame(GameType type, List<Long> participantIds, Set<Long> penalizedIds, long amount) {
		given(gameSeedGenerator.next()).willReturn(seedFor(participantIds, penalizedIds));
		Long gameId = api.createMoneyGame(chulsu, meetingId, type, participantIds, penalizedIds.size(), amount)
				.gameId();
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
