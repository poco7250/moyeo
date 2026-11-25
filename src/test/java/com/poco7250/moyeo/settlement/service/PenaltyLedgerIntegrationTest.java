package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.game.service.GameRerunService;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ChangeLogPageResponse;
import com.poco7250.moyeo.settlement.dto.ChangeLogResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseDeleteResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.NetBalanceChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionChangeResponse;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.settlement.dto.SettlementReconfirmedResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 금액형 게임 벌칙이 정산 계산·조회에 실리고, 지출 변경과 무관하게 유지되며, 면제 후 재확정이 리비전 +1과
 * 변동액을 만드는지 실제 DB로 확인한다(PRD S-1, S-4, S-10, S-11).
 * 구성: 2차 "볼링" 100,000원(결제 호스트), 참석 4명, 12,000원 사다리 참가자 민수·영희·지수 중 1명이 벌칙자.
 * 커밋 후 브로드캐스트와 FK를 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PenaltyLedgerIntegrationTest {

	private static long nextKakaoId = 970_000L;

	@Autowired
	ExpenseService expenseService;

	@Autowired
	SettlementQueryService settlementQueryService;

	@Autowired
	SettlementConfirmService settlementConfirmService;

	@Autowired
	PenaltyConditionService penaltyConditionService;

	@Autowired
	SettlementHistoryService settlementHistoryService;

	@Autowired
	ChangeLogQueryService changeLogQueryService;

	@Autowired
	GameLobbyService gameLobbyService;

	@Autowired
	GameRerunService gameRerunService;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;
	private Member host;
	private Member minsu;
	private Member yeonghee;
	private Member jisu;
	private Expense bowling;
	private Long gameId;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("철수");
		minsu = saveMember("민수");
		yeonghee = saveMember("영희");
		jisu = saveMember("지수");
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 2, "볼링장"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		for (Member member : List.of(host, minsu, yeonghee, jisu)) {
			attendanceRepository.save(Attendance.respond(round, member, AttendanceStatus.ATTEND,
					LocalDateTime.of(2026, 12, 1, 12, 0)));
		}
		bowling = expenseRepository.save(Expense.create(round, host, 100_000L, "볼링"));
		gameId = revealedGame();
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
	}

	@AfterEach
	void tearDown() {
		Long meetingId = meeting.getId();
		String revisionIds = "(select id from settlement_revision where settlement_id = " + settlement.getId() + ")";
		jdbcTemplate.update("delete from penalty_charge where revision_id in " + revisionIds);
		jdbcTemplate.update("delete from transfer where revision_id in " + revisionIds);
		jdbcTemplate.update("delete from revision_member_share where revision_id in " + revisionIds);
		jdbcTemplate.update("delete from settlement_revision where settlement_id = ?", settlement.getId());
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meetingId);
		String gameIds = "select id from game_session where meeting_id = " + meetingId;
		jdbcTemplate.update("delete from expense where round_id = ?", round.getId());
		jdbcTemplate.update("delete from penalty where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_result where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_participant where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update(
				"update game_session set rerun_of_id = null, canceled_rerun_of_id = null where meeting_id = ?",
				meetingId);
		jdbcTemplate.update("delete from game_session where meeting_id = ?", meetingId);
		jdbcTemplate.update("delete from attendance where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meetingId);
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), minsu.getId(), yeonghee.getId(), jisu.getId()));
	}

	@Test
	@DisplayName("12,000원 사다리: 벌칙자 +12,000·나머지 참가자 2명 −6,000씩이 정산 조회와 반영 내역에 실린다")
	void appliedGameIsCalculatedAndShown() {
		Long penalizedId = penalizedMemberId(gameId);
		List<Long> creditedIds = creditedIds(penalizedId);

		SettlementResponse response = settlementQueryService.get(meeting.getId(), host.getId());

		assertThat(shares(response)).containsEntry(penalizedId, 37_000L).containsEntry(host.getId(), 25_000L)
				.containsEntry(creditedIds.get(0), 19_000L).containsEntry(creditedIds.get(1), 19_000L).hasSize(4);
		assertThat(response.members().stream().mapToLong(SettlementMemberResponse::shareAmount).sum())
				.isEqualTo(100_000L);
		assertThat(response.penalties()).singleElement().satisfies(item -> {
			assertThat(item.memberId()).isEqualTo(penalizedId);
			assertThat(item.status()).isEqualTo(PenaltyStatus.APPLIED);
			assertThat(item.game()).isEqualTo(new SettlementPenaltyResponse.Game(gameId, GameType.LADDER, 12_000L));
			assertThat(item.guest()).isFalse();
			assertThat(item.chargeAmount()).isEqualTo(12_000L);
			assertThat(item.creditedMemberIds()).isEqualTo(creditedIds);
		});
	}

	@Test
	@DisplayName("지출 금액을 고쳐도 게임 벌칙은 그대로 12,000원이다(지출과 무관)")
	void expenseUpdateKeepsPenalty() {
		Long penalizedId = penalizedMemberId(gameId);

		expenseService.update(bowling.getId(), host.getId(), new ExpenseRequest(200_000L, host.getId(), "볼링"));

		SettlementResponse response = settlementQueryService.get(meeting.getId(), host.getId());
		assertThat(penaltyStatuses(gameId)).containsOnly("APPLIED");
		assertThat(response.penalties().getFirst().chargeAmount()).isEqualTo(12_000L);
		assertThat(shares(response)).containsEntry(penalizedId, 62_000L);
	}

	@Test
	@DisplayName("확정된 정산에서 지출을 삭제하면 REOPENED·지출 삭제 로그 1건이고, 벌칙은 그대로 남아 지출 없이도 반영된다")
	void deleteExpenseKeepsPenalty() {
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		Long penalizedId = penalizedMemberId(gameId);

		ExpenseDeleteResponse response = expenseService.delete(bowling.getId(), minsu.getId());

		assertThat(response).isEqualTo(new ExpenseDeleteResponse(bowling.getId()));
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(logs()).extracting(ChangeLog::getEntityType, ChangeLog::getChangeType)
				.containsExactly(tuple(ChangeLogEntityType.EXPENSE, ChangeType.DELETE));
		assertThat(penaltyStatuses(gameId)).containsOnly("APPLIED");
		// REOPENED 동안 조회는 직전 리비전을 보여 주므로 재확정해서 새 계산을 본다
		SettlementResponse after = settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		assertThat(shares(after)).containsEntry(penalizedId, 12_000L)
				.containsEntry(creditedIds(penalizedId).get(0), -6_000L);
	}

	@Test
	@DisplayName("다시 돌리면 원 회차 벌칙은 VOIDED로 반영 내역에서 빠지고 새 회차 벌칙이 같은 금액으로 반영된다")
	void rerunReplacesPenalty() {
		Long rerunId = gameRerunService.request(gameId, minsu.getId()).gameId();
		gameLobbyService.start(rerunId, minsu.getId());

		assertThat(penaltyStatuses(gameId)).containsOnly("VOIDED");
		List<SettlementPenaltyResponse> items = settlementQueryService.get(meeting.getId(), host.getId()).penalties();
		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.game().gameId()).isEqualTo(rerunId);
			assertThat(item.status()).isEqualTo(PenaltyStatus.APPLIED);
			assertThat(item.chargeAmount()).isEqualTo(12_000L);
		});
	}

	@Test
	@DisplayName("면제한 게임은 부담액 없이 WAIVED와 면제자·면제 시각으로 보이고 정산에 반영되지 않는다")
	void waivedShown() {
		penaltyConditionService.waive(gameId, host.getId());

		SettlementResponse response = settlementQueryService.get(meeting.getId(), host.getId());

		SettlementPenaltyResponse item = response.penalties().getFirst();
		assertThat(item.status()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(item.waivedBy()).isEqualTo(host.getId());
		assertThat(item.waivedAt()).isNotNull();
		assertThat(item.chargeAmount()).isNull();
		assertThat(shares(response).values()).containsOnly(25_000L);
	}

	@Test
	@DisplayName("리비전 1 확정 → 면제(REOPENED) → 재확정하면 리비전 2 송금 안내·변동액이 나오고 리비전 1은 그대로다")
	void reconfirmAfterWaive() {
		Long penalizedId = penalizedMemberId(gameId);
		List<Long> creditedIds = creditedIds(penalizedId);
		SettlementResponse revision1 = settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		List<String> revision1Transfers = transfersOf(1);

		penaltyConditionService.waive(gameId, host.getId());
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);

		SettlementResponse revision2 = settlementConfirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(revision2.status()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(revision2.basisRevisionNo()).isEqualTo(2);
		assertThat(revision2.transfers()).containsExactlyInAnyOrder(
				new SettlementTransferResponse(penalizedId, host.getId(), 25_000L),
				new SettlementTransferResponse(creditedIds.get(0), host.getId(), 25_000L),
				new SettlementTransferResponse(creditedIds.get(1), host.getId(), 25_000L));
		assertThat(revision2.revisionChange().previousRevisionNo()).isEqualTo(1);
		assertThat(revision2.revisionChange().notice()).isEqualTo(RevisionChangeResponse.NOTICE);
		assertThat(revision2.revisionChange().changes()).contains(
				new NetBalanceChangeResponse(penalizedId, -37_000L, -25_000L, 12_000L),
				new NetBalanceChangeResponse(creditedIds.get(0), -19_000L, -25_000L, -6_000L),
				new NetBalanceChangeResponse(creditedIds.get(1), -19_000L, -25_000L, -6_000L));
		assertThat(transfersOf(1)).isEqualTo(revision1Transfers).hasSize(3);
		assertThat(netBalancesOf(1)).containsEntry(penalizedId, -37_000L).containsEntry(host.getId(), 75_000L);
		assertThat(chargeAmountsOf(1)).containsExactly(12_000L);
		assertThat(chargeAmountsOf(2)).isEmpty();
		assertThat(revision1.revisionChange()).isNull();
		SettlementResponse queried = settlementQueryService.get(meeting.getId(), jisu.getId());
		assertThat(queried.basisRevisionNo()).isEqualTo(2);
		assertThat(queried.transfers()).isEqualTo(revision2.transfers());
	}

	@Test
	@DisplayName("재확정한 정산을 다시 확정하면 409 SETTLEMENT_ALREADY_CONFIRMED이고 리비전은 2개 그대로다")
	void reconfirmTwiceRejected() {
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		penaltyConditionService.waive(gameId, host.getId());
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);

		assertThatThrownBy(() -> settlementConfirmService.confirm(meeting.getId(), host.getId(), false))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SETTLEMENT_ALREADY_CONFIRMED);
		assertThat(jdbcTemplate.queryForObject("select count(*) from settlement_revision where settlement_id = ?",
				Integer.class, settlement.getId())).isEqualTo(2);
	}

	@Test
	@DisplayName("REOPENED 재확정도 다시 돌린 금액형 회차가 대기 중이면 409 MONEY_GAME_IN_PROGRESS이고 리비전 1만 있다")
	void reconfirmBlockedByWaitingRerun() {
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		gameRerunService.request(gameId, minsu.getId());
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);

		assertThatThrownBy(() -> settlementConfirmService.confirm(meeting.getId(), host.getId(), false))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MONEY_GAME_IN_PROGRESS);
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(jdbcTemplate.queryForObject("select count(*) from settlement_revision where settlement_id = ?",
				Integer.class, settlement.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("면제 후 재확정이 커밋되면 SETTLEMENT_RECONFIRMED 1회(변동 0 아닌 멤버·새 송금 안내·공유 문구), 최초 확정은 알림 없음")
	void reconfirmBroadcastsOnce() {
		Long penalizedId = penalizedMemberId(gameId);
		String penalizedName = nameOf(penalizedId);
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		penaltyConditionService.waive(gameId, host.getId());
		clearInvocations(messagingTemplate);

		SettlementResponse revision2 = settlementConfirmService.confirm(meeting.getId(), host.getId(), false);

		ArgumentCaptor<RealtimeMessage> captor = ArgumentCaptor.forClass(RealtimeMessage.class);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/meetings/" + meeting.getId()), captor.capture());
		RealtimeMessage message = captor.getValue();
		assertThat(message.type()).isEqualTo(SettlementReconfirmedEvent.TYPE);
		SettlementReconfirmedResponse data = (SettlementReconfirmedResponse) message.data();
		assertThat(data.revisionNo()).isEqualTo(2);
		assertThat(data.confirmedBy()).isEqualTo(host.getId());
		assertThat(data.changes()).extracting(NetBalanceChangeResponse::delta)
				.containsExactlyInAnyOrder(12_000L, -6_000L, -6_000L);
		assertThat(data.transfers()).isEqualTo(revision2.transfers());
		assertThat(data.shareText()).startsWith("정산이 수정됐어요(리비전 2): ")
				.contains(penalizedName + " +12,000원").endsWith(RevisionChangeResponse.NOTICE)
				.isEqualTo(revision2.revisionChange().shareText());
	}

	@Test
	@DisplayName("재확정이 게이트에서 막혀 롤백되면 재확정 알림을 보내지 않는다")
	void noBroadcastWhenReconfirmRejected() {
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		gameRerunService.request(gameId, minsu.getId());
		clearInvocations(messagingTemplate);

		assertThatThrownBy(() -> settlementConfirmService.confirm(meeting.getId(), host.getId(), false))
				.isInstanceOf(BusinessException.class);

		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("이력: 리비전 목록은 2건(리비전 1에 게임 금액 스냅샷, 리비전 2는 면제로 스냅샷 없음)이고 변경 로그는 면제를 보여 준다")
	void revisionsAndChangeLogs() {
		Long penalizedId = penalizedMemberId(gameId);
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);
		penaltyConditionService.waive(gameId, host.getId());
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);

		List<SettlementRevisionResponse> revisions = settlementHistoryService.revisions(meeting.getId(),
				jisu.getId());

		assertThat(revisions).extracting(SettlementRevisionResponse::revisionNo).containsExactly(1, 2);
		assertThat(revisions.get(0).changes()).isNull();
		assertThat(revisions.get(0).transfers()).hasSize(3);
		assertThat(revisions.get(0).penaltyCharges()).singleElement()
				.satisfies(charge -> {
					assertThat(charge.memberId()).isEqualTo(penalizedId);
					assertThat(charge.amount()).isEqualTo(12_000L);
					assertThat(charge.fixedAmount()).isEqualTo(12_000L);
					assertThat(charge.targetExpenseId()).isNull();
				});
		assertThat(revisions.get(1).changes()).contains(
				new NetBalanceChangeResponse(penalizedId, -37_000L, -25_000L, 12_000L));
		assertThat(revisions.get(1).penaltyCharges()).isEmpty();

		ChangeLogPageResponse logs = changeLogQueryService.list(meeting.getId(), jisu.getId(), 0, 50);
		assertThat(logs.hasNext()).isFalse();
		assertThat(logs.items()).extracting(ChangeLogResponse::entityType)
				.containsOnly(ChangeLogEntityType.PENALTY);
		assertThat(logs.items().getLast().before()).contains("\"status\":\"APPLIED\"");
		assertThat(logs.items().getLast().after()).contains("\"status\":\"WAIVED\"");
		assertThat(logs.items().getLast().settlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("그룹 밖 회원은 리비전 목록·변경 로그를 볼 수 없다(403 NOT_GROUP_MEMBER)")
	void historyRejectsOutsider() {
		Member outsider = memberRepository.save(Member.ofKakao(nextKakaoId++, "외부인", null, null));
		try {
			assertThatThrownBy(() -> settlementHistoryService.revisions(meeting.getId(), outsider.getId()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
			assertThatThrownBy(() -> changeLogQueryService.list(meeting.getId(), outsider.getId(), 0, 50))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		} finally {
			memberRepository.deleteById(outsider.getId());
		}
	}

	private Long revealedGame() {
		Long id = gameLobbyService.create(meeting.getId(), minsu.getId(), new GameCreateRequest(GameType.LADDER,
				PenaltyKind.MONEY, 12_000L, "꽝이 만 이천 원", List.of(minsu.getId(), yeonghee.getId(), jisu.getId()),
				null)).gameId();
		gameLobbyService.start(id, minsu.getId());
		return id;
	}

	private Map<Long, Long> shares(SettlementResponse response) {
		return response.members().stream()
				.collect(Collectors.toMap(SettlementMemberResponse::memberId, SettlementMemberResponse::shareAmount));
	}

	private Long penalizedMemberId(Long game) {
		return jdbcTemplate.queryForObject("select member_id from penalty where game_session_id = ?", Long.class,
				game);
	}

	// 사다리 참가자 민수·영희·지수 중 벌칙자를 뺀 두 사람(회원 id 오름차순)
	private List<Long> creditedIds(Long penalizedId) {
		return java.util.stream.Stream.of(minsu.getId(), yeonghee.getId(), jisu.getId())
				.filter(id -> !id.equals(penalizedId)).sorted().toList();
	}

	private String nameOf(Long memberId) {
		return memberId.equals(minsu.getId()) ? "민수" : memberId.equals(yeonghee.getId()) ? "영희" : "지수";
	}

	private List<String> penaltyStatuses(Long game) {
		return jdbcTemplate.queryForList("select status from penalty where game_session_id = ? order by id",
				String.class, game);
	}

	private SettlementStatus settlementStatus() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getStatus();
	}

	private List<ChangeLog> logs() {
		return changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
	}

	private String revisionIdOf(int revisionNo) {
		return "(select id from settlement_revision where settlement_id = " + settlement.getId()
				+ " and revision_no = " + revisionNo + ")";
	}

	private List<String> transfersOf(int revisionNo) {
		return jdbcTemplate.queryForList("select concat(from_member_id, '>', to_member_id, ':', amount) from transfer"
				+ " where revision_id = " + revisionIdOf(revisionNo) + " order by id", String.class);
	}

	private Map<Long, Long> netBalancesOf(int revisionNo) {
		return jdbcTemplate.queryForList("select member_id, net_balance from revision_member_share where revision_id = "
				+ revisionIdOf(revisionNo)).stream()
				.collect(Collectors.toMap(row -> ((Number) row.get("member_id")).longValue(),
						row -> ((Number) row.get("net_balance")).longValue()));
	}

	private List<Long> chargeAmountsOf(int revisionNo) {
		return jdbcTemplate.queryForList("select amount from penalty_charge where revision_id = "
				+ revisionIdOf(revisionNo), Long.class);
	}

	private Member saveMember(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
