package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.game.service.GameRerunService;
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
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleRequest;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ExpenseDeleteResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 벌칙 조건이 정산 계산·조회에 실리고, 대상 지출 삭제가 조건을 정리하는지 실제 DB로 확인한다(PRD S-1, S-4).
 * 예 1 구성: 2차 "볼링" 100,000원(결제 호스트), 참석 4명, 사다리 참가자 민수·영희 중 1명이 벌칙자.
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
		String gameIds = "select id from game_session where round_id = " + round.getId();
		jdbcTemplate.update("delete from penalty_rule where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from expense where round_id = ?", round.getId());
		jdbcTemplate.update("delete from penalty where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_result where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_participant where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("update game_session set rerun_of_id = null, canceled_rerun_of_id = null where round_id = ?",
				round.getId());
		jdbcTemplate.update("delete from game_session where round_id = ?", round.getId());
		jdbcTemplate.update("delete from attendance where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meetingId);
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), minsu.getId(), yeonghee.getId(), jisu.getId()));
	}

	@Test
	@DisplayName("예 1: RATIO 30% 조건을 걸면 정산 조회에 벌칙자 30,000원·결제자 23,200원·나머지 23,400원과 반영 내역이 실린다")
	void appliedRuleIsCalculatedAndShown() {
		penaltyConditionService.setRule(gameId, host.getId(), ratio30());
		Long penalizedId = penalizedMemberId(gameId);

		SettlementResponse response = settlementQueryService.get(meeting.getId(), host.getId());

		assertThat(shares(response)).containsEntry(penalizedId, 30_000L).containsEntry(host.getId(), 23_200L)
				.containsValues(23_400L).hasSize(4);
		assertThat(response.members().stream().mapToLong(SettlementMemberResponse::shareAmount).sum())
				.isEqualTo(100_000L);
		SettlementPenaltyResponse item = response.penalties().getFirst();
		assertThat(response.penalties()).hasSize(1);
		assertThat(item.memberId()).isEqualTo(penalizedId);
		assertThat(item.status()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(item.game()).isEqualTo(new SettlementPenaltyResponse.Game(gameId, GameType.LADDER, 2, "볼링장"));
		assertThat(item.rule().targetExpense())
				.isEqualTo(new SettlementPenaltyResponse.TargetExpense(bowling.getId(), 2, "볼링장", "볼링", 100_000L));
		assertThat(item.rule().setBy()).isEqualTo(host.getId());
		assertThat(item.rule().setAt()).isNotNull();
		assertThat(item.chargeAmount()).isEqualTo(30_000L);
	}

	@Test
	@DisplayName("대상 지출 금액을 고치면 벌칙 상태는 그대로이고 다음 계산에서 새 금액 기준 부담액이 나온다")
	void targetExpenseUpdateRecalculates() {
		penaltyConditionService.setRule(gameId, host.getId(), ratio30());

		expenseService.update(bowling.getId(), host.getId(), new ExpenseRequest(200_000L, host.getId(), "볼링"));

		assertThat(penaltyStatuses(gameId)).containsOnly("APPLIED");
		SettlementPenaltyResponse item = settlementQueryService.get(meeting.getId(), host.getId()).penalties()
				.getFirst();
		assertThat(item.chargeAmount()).isEqualTo(60_000L);
		assertThat(item.rule().targetExpense().amount()).isEqualTo(200_000L);
	}

	@Test
	@DisplayName("확정된 정산에서 대상 지출을 삭제하면 규칙 삭제·벌칙 ASSIGNED·REOPENED·삭제 전 값 로그·조건 초기화 이벤트 1회")
	void deleteTargetExpense() {
		penaltyConditionService.setRule(gameId, host.getId(), ratio30());
		Long ruleId = ruleIdOf(gameId);
		settlementConfirmService.confirm(meeting.getId(), host.getId(), true);
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());

		ExpenseDeleteResponse response = expenseService.delete(bowling.getId(), minsu.getId());

		assertThat(response).isEqualTo(new ExpenseDeleteResponse(bowling.getId(), List.of(gameId)));
		assertThat(ruleCount()).isZero();
		assertThat(penaltyStatuses(gameId)).containsOnly("ASSIGNED");
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);
		List<ChangeLog> logs = logs();
		assertThat(logs).extracting(ChangeLog::getEntityType, ChangeLog::getEntityId, ChangeLog::getChangeType)
				.containsExactly(
						tuple(ChangeLogEntityType.EXPENSE, bowling.getId(),
								ChangeType.DELETE),
						tuple(ChangeLogEntityType.PENALTY_RULE, ruleId,
								ChangeType.DELETE));
		assertThat(logs.get(1).getBeforeJson())
				.contains("\"ratioPct\":30", "\"targetExpenseId\":" + bowling.getId());
		assertThat(logs.get(1).getAfterJson()).isNull();
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(PenaltyConditionResetEvent.TYPE, response));
		SettlementPenaltyResponse item = settlementQueryService.get(meeting.getId(), host.getId()).penalties()
				.getFirst();
		assertThat(item.status()).isEqualTo(PenaltyStatus.ASSIGNED);
		assertThat(item.rule()).isNull();
		assertThat(item.chargeAmount()).isNull();
	}

	@Test
	@DisplayName("다시 돌려 이관된 규칙의 대상 지출을 삭제해도 FK 오류가 없고, 무효(VOIDED) 벌칙은 반영 내역에 없다")
	void deleteTargetOfTakenOverRule() {
		penaltyConditionService.setRule(gameId, host.getId(), ratio30());
		Long rerunId = gameRerunService.request(gameId, minsu.getId()).gameId();
		gameLobbyService.start(rerunId, minsu.getId());
		assertThat(penaltyStatuses(gameId)).containsOnly("VOIDED");
		assertThat(ruleCount(gameId)).isZero();
		assertThat(ruleCount(rerunId)).isOne();

		List<SettlementPenaltyResponse> before = settlementQueryService.get(meeting.getId(), host.getId()).penalties();
		assertThat(before).extracting(item -> item.game().gameId()).containsOnly(rerunId);
		assertThat(before).extracting(SettlementPenaltyResponse::status).containsOnly(PenaltyStatus.APPLIED);

		ExpenseDeleteResponse response = expenseService.delete(bowling.getId(), host.getId());

		assertThat(response.conditionResetGameIds()).containsExactly(rerunId);
		assertThat(ruleCount()).isZero();
		assertThat(penaltyStatuses(rerunId)).containsOnly("ASSIGNED");
		assertThat(penaltyStatuses(gameId)).containsOnly("VOIDED");
		assertThat(expenseRepository.findById(bowling.getId())).isEmpty();
	}

	@Test
	@DisplayName("면제한 게임은 금액 없이 WAIVED와 면제자·면제 시각으로 보이고 정산에 반영되지 않는다")
	void waivedShown() {
		penaltyConditionService.waive(gameId, host.getId());

		SettlementResponse response = settlementQueryService.get(meeting.getId(), host.getId());

		SettlementPenaltyResponse item = response.penalties().getFirst();
		assertThat(item.status()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(item.waivedBy()).isEqualTo(host.getId());
		assertThat(item.waivedAt()).isNotNull();
		assertThat(item.rule()).isNull();
		assertThat(shares(response).values()).containsOnly(25_000L);
	}

	@Test
	@DisplayName("대상이 아닌 지출을 삭제하면 조건은 그대로이고 조건 초기화 이벤트도 없다")
	void deleteNonTargetExpense() {
		penaltyConditionService.setRule(gameId, host.getId(), ratio30());
		Expense other = expenseRepository.save(Expense.create(round, host, 10_000L, "음료"));

		ExpenseDeleteResponse response = expenseService.delete(other.getId(), host.getId());

		assertThat(response.conditionResetGameIds()).isEmpty();
		assertThat(ruleCount()).isOne();
		assertThat(penaltyStatuses(gameId)).containsOnly("APPLIED");
	}

	private Long revealedGame() {
		Long id = gameLobbyService.create(round.getId(), minsu.getId(), new GameCreateRequest(GameType.LADDER,
				PenaltyKind.MONEY, "꼴찌가 볼링 30%", List.of(minsu.getId(), yeonghee.getId()), null)).gameId();
		gameLobbyService.start(id, minsu.getId());
		return id;
	}

	private PenaltyRuleRequest ratio30() {
		return new PenaltyRuleRequest(PenaltyMoneyType.RATIO, bowling.getId(), null, 30);
	}

	private Map<Long, Long> shares(SettlementResponse response) {
		return response.members().stream()
				.collect(Collectors.toMap(SettlementMemberResponse::memberId, SettlementMemberResponse::shareAmount));
	}

	private Long penalizedMemberId(Long game) {
		return jdbcTemplate.queryForObject("select member_id from penalty where game_session_id = ?", Long.class,
				game);
	}

	private Long ruleIdOf(Long game) {
		return jdbcTemplate.queryForObject("select id from penalty_rule where game_session_id = ?", Long.class, game);
	}

	private int ruleCount() {
		String gameIds = "select id from game_session where round_id = " + round.getId();
		return jdbcTemplate.queryForObject(
				"select count(*) from penalty_rule where game_session_id in (" + gameIds + ")", Integer.class);
	}

	private int ruleCount(Long game) {
		return jdbcTemplate.queryForObject("select count(*) from penalty_rule where game_session_id = ?",
				Integer.class, game);
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

	private Member saveMember(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
