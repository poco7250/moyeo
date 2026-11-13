package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.game.service.GameRerunService;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
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
import com.poco7250.moyeo.penalty.dto.PenaltyRuleRequest;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.InProgressGameResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.dto.UnsetPenaltyGameResponse;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 정산 확정 게이트(PRD S-10)와 PenaltyCharge 스냅샷을 실제 DB로 확인한다.
 * 예 1 구성: 2차 "볼링" 100,000원(결제 호스트), 참석 4명, 금액형 사다리 참가자 민수·영희 중 1명이 벌칙자.
 * 커밋과 FK를 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementConfirmGateIntegrationTest {

	private static long nextKakaoId = 980_000L;

	@Autowired
	SettlementConfirmService confirmService;

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
	ExpenseRepository expenseRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SettlementInputAssembler inputAssembler;

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
	}

	@AfterEach
	void tearDown() {
		String revisionIds = "(select id from settlement_revision where settlement_id = " + settlement.getId() + ")";
		jdbcTemplate.update("delete from penalty_charge where revision_id in " + revisionIds);
		jdbcTemplate.update("delete from transfer where revision_id in " + revisionIds);
		jdbcTemplate.update("delete from revision_member_share where revision_id in " + revisionIds);
		jdbcTemplate.update("delete from settlement_revision where settlement_id = ?", settlement.getId());
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
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
		meetingRepository.deleteById(meeting.getId());
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), minsu.getId(), yeonghee.getId(), jisu.getId()));
	}

	@Test
	@DisplayName("금액형 ASSIGNED가 남으면 409 PENALTY_CONDITION_UNSET과 목록, 정산 그대로 → 조건 입력 후 확정되고 PenaltyCharge가 남는다")
	void unsetBlocksThenRuleConfirms() {
		long versionBefore = settlementVersion();
		Long penaltyId = penaltyIdOf(gameId);
		Long penalizedId = penalizedMemberId(gameId);

		assertRejected(false, ErrorCode.PENALTY_CONDITION_UNSET, List.of(new UnsetPenaltyGameResponse(gameId,
				GameType.LADDER, 2, "볼링장", List.of(penaltyId), List.of(penalizedId))));
		assertThat(currentStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(settlementVersion()).isEqualTo(versionBefore);
		assertThat(count("settlement_revision where settlement_id = " + settlement.getId())).isZero();

		penaltyConditionService.setRule(gameId, host.getId(), new PenaltyRuleRequest(PenaltyMoneyType.RATIO,
				bowling.getId(), null, 30));
		confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(currentStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		Long revisionId = revisionId();
		assertThat(count("revision_member_share where revision_id = " + revisionId)).isEqualTo(4);
		assertThat(count("transfer where revision_id = " + revisionId)).isEqualTo(3);
		Map<String, Object> charge = jdbcTemplate.queryForMap(
				"select * from penalty_charge where revision_id = ?", revisionId);
		assertThat(charge).containsEntry("penalty_id", penaltyId)
				.containsEntry("amount", 30_000L)
				.containsEntry("money_type", "RATIO")
				.containsEntry("ratio_pct", 30)
				.containsEntry("fixed_amount", null)
				.containsEntry("target_expense_id", bowling.getId())
				.containsEntry("target_expense_amount", 100_000L)
				.containsEntry("target_expense_memo", "볼링");
	}

	@Test
	@DisplayName("금액 벌칙 없음(면제)을 고르면 확정되고 PenaltyCharge는 남지 않는다")
	void waiveThenConfirms() {
		penaltyConditionService.waive(gameId, host.getId());

		confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(currentStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(count("penalty_charge where revision_id = " + revisionId())).isZero();
	}

	@Test
	@DisplayName("행동형 ASSIGNED 벌칙만 있으면 확정을 막지 않는다")
	void actionPenaltyDoesNotBlock() {
		penaltyConditionService.waive(gameId, host.getId());
		Long actionGameId = gameLobbyService.create(round.getId(), minsu.getId(), new GameCreateRequest(
				GameType.LADDER, PenaltyKind.ACTION, "노래 한 곡", List.of(minsu.getId(), yeonghee.getId()), null))
				.gameId();
		gameLobbyService.start(actionGameId, minsu.getId());

		confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(currentStatus()).isEqualTo(SettlementStatus.CONFIRMED);
	}

	@Test
	@DisplayName("다시 돌린 금액형 회차가 WAITING이면 409 MONEY_GAME_IN_PROGRESS와 게임 목록, 취소하면 확정된다")
	void rerunWaitingBlocksUntilCanceled() {
		penaltyConditionService.setRule(gameId, host.getId(), new PenaltyRuleRequest(PenaltyMoneyType.RATIO,
				bowling.getId(), null, 30));
		Long rerunId = gameRerunService.request(gameId, minsu.getId()).gameId();

		assertRejected(false, ErrorCode.MONEY_GAME_IN_PROGRESS, List.of(new InProgressGameResponse(rerunId,
				GameType.LADDER, GameStatus.WAITING, 2, "볼링장", gameId)));
		assertThat(currentStatus()).isEqualTo(SettlementStatus.DRAFT);

		gameLobbyService.cancel(rerunId, minsu.getId());
		confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(currentStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(count("penalty_charge where revision_id = " + revisionId())).isOne();
	}

	@Test
	@DisplayName("예 3: 고정 150,000원 벌칙은 경고 미확인 시 409 WARNING_UNCONFIRMED와 경고 내용, 확인하면 100,000원으로 확정된다")
	void example3WarningAcknowledged() {
		penaltyConditionService.setRule(gameId, host.getId(), new PenaltyRuleRequest(
				PenaltyMoneyType.FIXED_AMOUNT, bowling.getId(), 150_000L, null));

		assertRejected(false, ErrorCode.WARNING_UNCONFIRMED, List.of(new SettlementWarningResponse(
				SettlementWarningType.PENALTY_EXCEEDS_TARGET, bowling.getId())));
		assertThat(currentStatus()).isEqualTo(SettlementStatus.DRAFT);

		confirmService.confirm(meeting.getId(), host.getId(), true);

		Long revisionId = revisionId();
		assertThat(jdbcTemplate.queryForObject("select warnings_acknowledged from settlement_revision where id = ?",
				Boolean.class, revisionId)).isTrue();
		assertThat(jdbcTemplate.queryForMap("select amount, fixed_amount from penalty_charge where revision_id = ?",
				revisionId)).containsEntry("amount", 100_000L).containsEntry("fixed_amount", 150_000L);
	}

	@Test
	@DisplayName("확정 검사 뒤 면제가 먼저 커밋되면 확정은 충돌로 실패하고 리비전 없이 면제만 남는다")
	void waiveBeatsConfirm() {
		penaltyConditionService.setRule(gameId, host.getId(), new PenaltyRuleRequest(PenaltyMoneyType.RATIO,
				bowling.getId(), null, 30));
		doAnswer(invocation -> {
			Object input = invocation.callRealMethod();
			CompletableFuture.runAsync(() -> penaltyConditionService.waive(gameId, host.getId())).join();
			return input;
		}).when(inputAssembler).assemble(eq(meeting.getId()), any(MeetingPenalties.class));

		assertThatThrownBy(() -> confirmService.confirm(meeting.getId(), host.getId(), false))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(count("settlement_revision where settlement_id = " + settlement.getId())).isZero();
		assertThat(currentStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(jdbcTemplate.queryForList("select status from penalty where game_session_id = ?", String.class,
				gameId)).containsOnly("WAIVED");
	}

	private void assertRejected(boolean acknowledge, ErrorCode errorCode, Object details) {
		assertThatThrownBy(() -> confirmService.confirm(meeting.getId(), host.getId(), acknowledge))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(errorCode);
					assertThat(e.getDetails()).isEqualTo(details);
				});
	}

	private Long revealedGame() {
		Long id = gameLobbyService.create(round.getId(), minsu.getId(), new GameCreateRequest(GameType.LADDER,
				PenaltyKind.MONEY, "꼴찌가 볼링 30%", List.of(minsu.getId(), yeonghee.getId()), null)).gameId();
		gameLobbyService.start(id, minsu.getId());
		return id;
	}

	private Long penaltyIdOf(Long game) {
		return jdbcTemplate.queryForObject("select id from penalty where game_session_id = ?", Long.class, game);
	}

	private Long penalizedMemberId(Long game) {
		return jdbcTemplate.queryForObject("select member_id from penalty where game_session_id = ?", Long.class,
				game);
	}

	private Long revisionId() {
		return jdbcTemplate.queryForObject("select id from settlement_revision where settlement_id = ?", Long.class,
				settlement.getId());
	}

	private int count(String fromWhere) {
		return jdbcTemplate.queryForObject("select count(*) from " + fromWhere, Integer.class);
	}

	private SettlementStatus currentStatus() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getStatus();
	}

	private long settlementVersion() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getVersion();
	}

	private Member saveMember(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
