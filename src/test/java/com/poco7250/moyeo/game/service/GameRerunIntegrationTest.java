package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameParticipantResponse;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
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
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 커밋과 rerunOf 유니크 충돌을 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GameRerunIntegrationTest {

	private static long nextKakaoId = 950_000L;

	@Autowired
	GameLobbyService gameLobbyService;

	@Autowired
	GameRerunService gameRerunService;

	@Autowired
	GameQueryService gameQueryService;

	@Autowired
	GameSessionRepository gameSessionRepository;

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
	PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Group group;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;
	private Member host;
	private Member creator;
	private Member third;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("호스트");
		creator = saveMember("민수");
		third = saveMember("영희");
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		for (Member member : List.of(host, creator, third)) {
			attendanceRepository.save(Attendance.respond(round, member, AttendanceStatus.ATTEND,
					LocalDateTime.of(2026, 12, 1, 12, 0)));
		}
	}

	@AfterEach
	void tearDown() {
		Long meetingId = meeting.getId();
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meetingId);
		String gameIds = "select id from game_session where round_id = " + round.getId();
		jdbcTemplate.update("delete from penalty_rule where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from expense where round_id = ?", round.getId());
		jdbcTemplate.update("delete from penalty where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_result where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_participant where game_session_id in (" + gameIds + ")");
		// 회차끼리 자기 참조 FK가 있어 먼저 끊고 지운다
		jdbcTemplate.update("update game_session set rerun_of_id = null, canceled_rerun_of_id = null where round_id = ?",
				round.getId());
		jdbcTemplate.update("delete from game_session where round_id = ?", round.getId());
		jdbcTemplate.update("delete from attendance where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meetingId);
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), creator.getId(), third.getId()));
	}

	@Test
	@DisplayName("호스트가 다시 돌리면 원 회차를 복제한 WAITING 회차가 생기고 적용 순서와 원 회차 id를 이어받는다")
	void rerunCopiesOriginal() {
		GameStateResponse original = revealedGame(PenaltyKind.ACTION);

		GameStateResponse rerun = gameRerunService.request(original.gameId(), host.getId());

		assertThat(rerun.status()).isEqualTo(GameStatus.WAITING);
		assertThat(rerun.rerunOfId()).isEqualTo(original.gameId());
		assertThat(rerun.rootSessionId()).isEqualTo(original.gameId());
		assertThat(rerun.createdBy()).isEqualTo(host.getId());
		assertThat(rerun.type()).isEqualTo(original.type());
		assertThat(rerun.penaltyKind()).isEqualTo(original.penaltyKind());
		assertThat(rerun.penaltyNote()).isEqualTo(original.penaltyNote());
		assertThat(rerun.participants()).extracting(GameParticipantResponse::memberId)
				.containsExactlyElementsOf(original.participants().stream()
						.map(GameParticipantResponse::memberId).toList());
		assertThat(rerun.result()).isNull();
		GameSession saved = gameSessionRepository.findById(rerun.gameId()).orElseThrow();
		GameSession savedOriginal = gameSessionRepository.findById(original.gameId()).orElseThrow();
		assertThat(saved.getApplyOrderAt()).isEqualTo(savedOriginal.getApplyOrderAt());
		assertThat(savedOriginal.getStatus()).isEqualTo(GameStatus.REVEALED);
		assertThat(penaltyStatuses(original.gameId())).containsExactly("ASSIGNED");
		assertThat(penaltyStatuses(rerun.gameId())).isEmpty();
	}

	@Test
	@DisplayName("진행자·호스트가 아닌 그룹 멤버가 요청하면 403이고 새 회차가 생기지 않는다")
	void rerunRejectsNonOperator() {
		GameStateResponse original = revealedGame(PenaltyKind.ACTION);

		assertRejected(() -> gameRerunService.request(original.gameId(), third.getId()), ErrorCode.NOT_GAME_OPERATOR);

		assertThat(gameSessionRepository.existsByRerunOfId(original.gameId())).isFalse();
	}

	@Test
	@DisplayName("공개 전 회차와 이미 후속 회차가 있는 회차는 다시 돌릴 수 없다")
	void rerunRejectsNotRerunnable() {
		GameStateResponse waiting = createGame(PenaltyKind.ACTION);
		assertRejected(() -> gameRerunService.request(waiting.gameId(), host.getId()), ErrorCode.GAME_NOT_RERUNNABLE);

		GameStateResponse original = revealedGame(PenaltyKind.ACTION);
		gameRerunService.request(original.gameId(), host.getId());
		assertRejected(() -> gameRerunService.request(original.gameId(), host.getId()), ErrorCode.GAME_NOT_RERUNNABLE);

		assertThat(gameQueryService.getRoundGames(round.getId(), host.getId())).hasSize(3);
	}

	@RepeatedTest(5)
	@DisplayName("같은 회차에 다시 돌리기가 동시에 오면 1건만 성공하고 늦은 쪽은 409다")
	void concurrentRerun() {
		GameStateResponse original = revealedGame(PenaltyKind.MONEY);
		CountDownLatch ready = new CountDownLatch(2);

		List<CompletableFuture<Throwable>> requests = List.of(rerunAsync(original.gameId(), ready),
				rerunAsync(original.gameId(), ready));

		List<Throwable> failures = requests.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
		assertThat(failures).hasSize(1);
		assertThat(failures.getFirst()).isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isIn(ErrorCode.RERUN_ALREADY_EXISTS, ErrorCode.GAME_NOT_RERUNNABLE);
		assertThat(jdbcTemplate.queryForObject("select count(*) from game_session where rerun_of_id = ?",
				Integer.class, original.gameId())).isEqualTo(1);
	}

	@Test
	@DisplayName("확정된 정산의 금액형 게임을 다시 돌리면 같은 트랜잭션에서 REOPENED가 되고 변경 로그가 남는다")
	void moneyRerunReopensSettlement() {
		GameStateResponse original = revealedGame(PenaltyKind.MONEY);
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		jdbcTemplate.update("update settlement set status = 'CONFIRMED' where id = ?", settlement.getId());

		GameStateResponse rerun = gameRerunService.request(original.gameId(), creator.getId());

		assertThat(settlementRepository.findById(settlement.getId()).orElseThrow().getStatus())
				.isEqualTo(SettlementStatus.REOPENED);
		List<ChangeLog> logs = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
		assertThat(logs).hasSize(1);
		ChangeLog log = logs.getFirst();
		assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.GAME_SESSION);
		assertThat(log.getEntityId()).isEqualTo(rerun.gameId());
		assertThat(log.getChangeType()).isEqualTo(ChangeType.CREATE);
		assertThat(log.getActorId()).isEqualTo(creator.getId());
		assertThat(log.getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(log.getAfterJson()).contains("\"rerunOfId\":" + original.gameId());
		assertThat(penaltyStatuses(original.gameId())).containsExactly("ASSIGNED");
	}

	@Test
	@DisplayName("다시 돌린 회차를 취소하면 원 회차 벌칙은 그대로고 원 회차를 다시 돌릴 수 있으며 취소 이력이 목록에 보인다")
	void cancelRerunKeepsOriginal() {
		GameStateResponse original = revealedGame(PenaltyKind.MONEY);
		GameStateResponse rerun = gameRerunService.request(original.gameId(), host.getId());

		GameStateResponse canceled = gameLobbyService.cancel(rerun.gameId(), host.getId());

		assertThat(canceled.status()).isEqualTo(GameStatus.CANCELED);
		assertThat(canceled.rerunOfId()).isNull();
		assertThat(canceled.canceledRerunOfId()).isEqualTo(original.gameId());
		assertThat(penaltyStatuses(original.gameId())).containsExactly("ASSIGNED");

		GameStateResponse second = gameRerunService.request(original.gameId(), host.getId());

		assertThat(second.rerunOfId()).isEqualTo(original.gameId());
		List<GameStateResponse> games = gameQueryService.getRoundGames(round.getId(), third.getId());
		assertThat(games).extracting(GameStateResponse::gameId)
				.containsExactly(second.gameId(), rerun.gameId(), original.gameId());
		assertThat(games).extracting(GameStateResponse::canceledRerunOfId)
				.containsExactly(null, original.gameId(), null);
		assertThat(games).extracting(GameStateResponse::rootSessionId).containsOnly(original.gameId());
	}

	@Test
	@DisplayName("조건이 적용된 금액형 회차를 다시 돌려 공개하면 원 벌칙은 VOIDED로 남고 규칙이 새 회차로 옮겨지며 새 벌칙자는 APPLIED다")
	void rerunRevealMovesRuleAndInheritsApplied() {
		GameStateResponse original = revealedGame(PenaltyKind.MONEY);
		LocalDateTime ruleSetAt = LocalDateTime.of(2026, 12, 20, 22, 0);
		Expense bowling = expenseRepository.save(Expense.create(round, host, 87_650L, "볼링"));
		PenaltyRule rule = penaltyRuleRepository.save(PenaltyRule.create(
				gameSessionRepository.findById(original.gameId()).orElseThrow(), PenaltyMoneyType.RATIO, null, 30,
				bowling, host.getId(), ruleSetAt));
		jdbcTemplate.update("update penalty set status = 'APPLIED' where game_session_id = ?", original.gameId());
		GameStateResponse rerun = gameRerunService.request(original.gameId(), host.getId());
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		jdbcTemplate.update("update settlement set status = 'CONFIRMED' where id = ?", settlement.getId());

		GameStateResponse revealed = gameLobbyService.start(rerun.gameId(), host.getId());

		assertThat(revealed.status()).isEqualTo(GameStatus.REVEALED);
		assertThat(penaltyStatuses(original.gameId())).containsExactly("VOIDED");
		assertThat(penaltyStatuses(rerun.gameId())).containsExactly("APPLIED");
		assertThat(jdbcTemplate.queryForObject("select count(*) from penalty_rule where game_session_id = ?",
				Integer.class, original.gameId())).isZero();
		Map<String, Object> moved = jdbcTemplate.queryForMap(
				"select id, set_by, updated_at, target_expense_id from penalty_rule where game_session_id = ?",
				rerun.gameId());
		assertThat(moved.get("id")).isEqualTo(rule.getId());
		assertThat(moved.get("set_by")).isEqualTo(host.getId());
		assertThat(moved.get("updated_at")).isEqualTo(ruleSetAt);
		assertThat(moved.get("target_expense_id")).isEqualTo(bowling.getId());
		assertThat(settlementRepository.findById(settlement.getId()).orElseThrow().getStatus())
				.isEqualTo(SettlementStatus.REOPENED);
		List<ChangeLog> logs = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
		assertThat(logs).extracting(ChangeLog::getEntityType)
				.containsExactly(ChangeLogEntityType.PENALTY_RULE, ChangeLogEntityType.GAME_SESSION);
		assertThat(logs).extracting(ChangeLog::getSettlementStatusAfter).containsOnly(SettlementStatus.REOPENED);
		assertThat(logs.get(0).getBeforeJson()).contains("\"gameSessionId\":" + original.gameId());
		assertThat(logs.get(0).getAfterJson()).contains("\"gameSessionId\":" + rerun.gameId());
		assertThat(logs.get(1).getAfterJson())
				.contains("\"rerunOfId\":" + original.gameId(), "\"inheritedStatus\":\"APPLIED\"");
	}

	@Test
	@DisplayName("면제된 금액형 회차를 다시 돌려 공개하면 새 벌칙자가 면제자와 면제 시각을 이어받는다")
	void rerunRevealInheritsWaived() {
		GameStateResponse original = revealedGame(PenaltyKind.MONEY);
		LocalDateTime waivedAt = LocalDateTime.of(2026, 12, 20, 22, 30);
		jdbcTemplate.update("update penalty set status = 'WAIVED', waived_by = ?, waived_at = ? "
				+ "where game_session_id = ?", host.getId(), waivedAt, original.gameId());
		GameStateResponse rerun = gameRerunService.request(original.gameId(), host.getId());

		gameLobbyService.start(rerun.gameId(), host.getId());

		assertThat(penaltyStatuses(original.gameId())).containsExactly("VOIDED");
		Map<String, Object> inherited = jdbcTemplate.queryForMap(
				"select status, waived_by, waived_at from penalty where game_session_id = ?", rerun.gameId());
		assertThat(inherited.get("status")).isEqualTo("WAIVED");
		assertThat(inherited.get("waived_by")).isEqualTo(host.getId());
		assertThat(inherited.get("waived_at")).isEqualTo(waivedAt);
		// 원 벌칙의 면제 정보는 이력으로 남는다
		assertThat(jdbcTemplate.queryForObject("select waived_by from penalty where game_session_id = ?",
				Long.class, original.gameId())).isEqualTo(host.getId());
	}

	@Test
	@DisplayName("행동형 회차를 다시 돌려 공개하면 원 벌칙만 VOIDED가 되고 정산은 그대로다")
	void actionRerunRevealLeavesSettlement() {
		GameStateResponse original = revealedGame(PenaltyKind.ACTION);
		GameStateResponse rerun = gameRerunService.request(original.gameId(), host.getId());
		jdbcTemplate.update("update settlement set status = 'CONFIRMED' where id = ?", settlement.getId());
		Long versionBefore = settlementRepository.findById(settlement.getId()).orElseThrow().getVersion();

		gameLobbyService.start(rerun.gameId(), host.getId());

		assertThat(penaltyStatuses(original.gameId())).containsExactly("VOIDED");
		assertThat(penaltyStatuses(rerun.gameId())).containsExactly("ASSIGNED");
		Settlement after = settlementRepository.findById(settlement.getId()).orElseThrow();
		assertThat(after.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(after.getVersion()).isEqualTo(versionBefore);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).isEmpty();
	}

	private CompletableFuture<Throwable> rerunAsync(Long gameId, CountDownLatch ready) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				ready.countDown();
				ready.await();
				gameRerunService.request(gameId, host.getId());
				return null;
			} catch (RuntimeException e) {
				return e;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return e;
			}
		});
	}

	private GameStateResponse revealedGame(PenaltyKind penaltyKind) {
		return gameLobbyService.start(createGame(penaltyKind).gameId(), creator.getId());
	}

	private GameStateResponse createGame(PenaltyKind penaltyKind) {
		return gameLobbyService.create(round.getId(), creator.getId(), new GameCreateRequest(GameType.LADDER,
				penaltyKind, "꼴찌가 2차 30%", List.of(creator.getId(), third.getId()), null));
	}

	private List<String> penaltyStatuses(Long gameId) {
		return jdbcTemplate.queryForList(
				"select status from penalty where game_session_id = ? order by id", String.class, gameId);
	}

	private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
		assertThatThrownBy(action)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}

	private Member saveMember(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
