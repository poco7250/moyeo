package com.poco7250.moyeo.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.service.GameLobbyService;
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
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 커밋으로 REOPENED 전이·충돌을 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PenaltyConditionIntegrationTest {

	private static long nextKakaoId = 960_000L;

	@Autowired
	PenaltyConditionService penaltyConditionService;

	@Autowired
	GameLobbyService gameLobbyService;

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
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Group group;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;
	private Member host;
	private Member creator;
	private Member third;
	private Expense bowling;
	private Long gameId;

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
		bowling = expenseRepository.save(Expense.create(round, host, 87_650L, "볼링"));
		gameId = gameLobbyService.create(meeting.getId(), creator.getId(), new GameCreateRequest(GameType.LADDER,
				PenaltyKind.MONEY, 10_000L, "꽝이 만 원", List.of(creator.getId(), third.getId()), null)).gameId();
		gameLobbyService.start(gameId, creator.getId());
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
	}

	@AfterEach
	void tearDown() {
		Long meetingId = meeting.getId();
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meetingId);
		String gameIds = "select id from game_session where meeting_id = " + meetingId;
		jdbcTemplate.update("delete from expense where round_id = ?", round.getId());
		jdbcTemplate.update("delete from penalty where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_result where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_participant where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_session where meeting_id = ?", meetingId);
		jdbcTemplate.update("delete from attendance where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meetingId);
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), creator.getId(), third.getId()));
	}

	@Test
	@DisplayName("공개된 금액형 게임 벌칙은 APPLIED이고, 확정된 정산에서 면제하면 REOPENED가 되고 변경 로그 1건이 남는다")
	void waiveReopensConfirmedSettlement() {
		assertThat(penaltyStatuses()).containsOnly("APPLIED");
		confirmSettlement();

		PenaltyConditionResponse response = penaltyConditionService.waive(gameId, host.getId());

		assertThat(response.status()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(response.penaltyAmount()).isEqualTo(10_000L);
		Map<String, Object> penalty = jdbcTemplate.queryForMap(
				"select status, waived_by, waived_at from penalty where game_session_id = ?", gameId);
		assertThat(penalty.get("status")).isEqualTo("WAIVED");
		assertThat(penalty.get("waived_by")).isEqualTo(host.getId());
		assertThat(penalty.get("waived_at")).isNotNull();
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);
		List<ChangeLog> logs = logs();
		assertThat(logs).singleElement().satisfies(log -> {
			assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.PENALTY);
			assertThat(log.getChangeType()).isEqualTo(ChangeType.UPDATE);
			assertThat(log.getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
			assertThat(log.getBeforeJson()).contains("\"status\":\"APPLIED\"");
			assertThat(log.getAfterJson()).contains("\"status\":\"WAIVED\"", "\"waivedBy\":" + host.getId());
		});
	}

	@Test
	@DisplayName("면제를 취소하면 APPLIED로 돌아가고 면제 정보가 비워진다")
	void unwaive() {
		penaltyConditionService.waive(gameId, host.getId());

		PenaltyConditionResponse response = penaltyConditionService.unwaive(gameId, host.getId());

		assertThat(response.status()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(jdbcTemplate.queryForMap("select status, waived_by from penalty where game_session_id = ?", gameId))
				.containsEntry("status", "APPLIED").containsEntry("waived_by", null);
	}

	@Test
	@DisplayName("정산 권한이 없는 멤버의 면제는 거부되고 아무것도 바뀌지 않는다")
	void rejectsNonHost() {
		assertRejected(() -> penaltyConditionService.waive(gameId, creator.getId()), ErrorCode.NOT_MEETING_HOST);

		assertThat(penaltyStatuses()).containsOnly("APPLIED");
		assertThat(logs()).isEmpty();
	}

	@Test
	@DisplayName("면제 2건이 겹치면 먼저 커밋한 쪽만 반영되고 늦은 쪽은 실패한다")
	void overlappingWaive() {
		TransactionTemplate late = new TransactionTemplate(transactionManager);
		TransactionTemplate first = new TransactionTemplate(transactionManager);
		first.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		// 늦은 쪽이 먼저 읽기 시점을 잡은 뒤(REPEATABLE READ) 다른 요청이 끼어들어 커밋한 상황을 만든다
		assertThatThrownBy(() -> late.executeWithoutResult(status -> {
			settlementRepository.count();
			first.executeWithoutResult(inner -> penaltyConditionService.waive(gameId, host.getId()));
			penaltyConditionService.waive(gameId, host.getId());
		})).isInstanceOfAny(BusinessException.class, OptimisticLockingFailureException.class);

		assertThat(penaltyStatuses()).containsOnly("WAIVED");
		assertThat(logs()).hasSize(1);
	}

	private void confirmSettlement() {
		jdbcTemplate.update("update settlement set status = 'CONFIRMED' where id = ?", settlement.getId());
	}

	private SettlementStatus settlementStatus() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getStatus();
	}

	private List<ChangeLog> logs() {
		return changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
	}

	private List<String> penaltyStatuses() {
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
