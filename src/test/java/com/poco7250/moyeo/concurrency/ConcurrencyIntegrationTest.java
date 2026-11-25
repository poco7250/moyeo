package com.poco7250.moyeo.concurrency;

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
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.domain.InviteLink;
import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.group.repository.InviteLinkRepository;
import com.poco7250.moyeo.group.service.InviteJoinService;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.meeting.service.AttendanceService;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.MeetingPenalties;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementInputAssembler;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.support.ConcurrentRunner;
import com.poco7250.moyeo.support.ConcurrentRunner.Outcome;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * PRD 6장 락 표 중 기존 테스트가 다루지 않던 충돌 상황을 실제 DB에서 동시에 실행해 본다(Task 042).
 * 실제 커밋 순서를 봐야 해서 테스트 트랜잭션을 쓰지 않고, 끝나면 전체 테이블을 비운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrencyIntegrationTest {

	private static final LocalDateTime START_AT = LocalDateTime.of(2099, 12, 20, 18, 0);
	private static final String INVITE_TOKEN = "concurrency-invite-token";

	@Autowired
	InviteJoinService inviteJoinService;

	@Autowired
	AttendanceService attendanceService;

	@Autowired
	SettlementConfirmService confirmService;

	@Autowired
	PenaltyConditionService penaltyConditionService;

	@Autowired
	GameLobbyService gameLobbyService;

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
	AttendanceRepository attendanceRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SettlementInputAssembler inputAssembler;

	private Member host;
	private Member other;
	private Member late;
	private Group group;
	private Meeting meeting;
	private Round round;
	private Expense expense;

	@BeforeEach
	void setUp() {
		host = saveMember("철수");
		other = saveMember("지수");
		late = saveMember("민수");
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.saveAll(List.of(join(host), join(other), join(late)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", START_AT, null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlementRepository.save(Settlement.createDraft(meeting));
		attendanceRepository.saveAll(List.of(attend(host), attend(other)));
		expense = expenseRepository.save(Expense.create(round, host, 90_000L, "볼링"));
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@RepeatedTest(5)
	@DisplayName("같은 회원이 같은 초대 링크로 동시에 5번 가입해도 전부 성공하고 멤버십은 1건, 새 가입 응답도 1건이다")
	void concurrentJoin() {
		Member newcomer = saveMember("영희");
		inviteLinkRepository.save(InviteLink.issue(group, INVITE_TOKEN, START_AT));

		List<Outcome<InviteJoinResponse>> outcomes =
				ConcurrentRunner.runTogether(5, () -> inviteJoinService.join(INVITE_TOKEN, newcomer.getId()));

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
		assertThat(outcomes).filteredOn(outcome -> outcome.value().joined()).hasSize(1);
		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.value().groupId()).isEqualTo(group.getId()));
		assertThat(count("select count(*) from group_member where group_id = ? and member_id = ?",
				group.getId(), newcomer.getId())).isEqualTo(1);
	}

	@RepeatedTest(5)
	@DisplayName("지출 있는 차수에 같은 회원의 첫 참석 응답 2건이 동시에 와도 둘 다 성공하고 행은 1건이다")
	void concurrentFirstAttendance() {
		List<Callable<MemberAttendanceResponse>> responses = List.of(
				() -> respond(late, AttendanceStatus.ATTEND),
				() -> respond(late, AttendanceStatus.ABSENT));

		List<Outcome<MemberAttendanceResponse>> outcomes = ConcurrentRunner.runTogether(responses);

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
		List<Attendance> rows = attendanceRepository.findAllByMeetingIdAndMemberId(meeting.getId(), late.getId());
		assertThat(rows).singleElement().extracting(Attendance::getStatus)
				.isIn(AttendanceStatus.ATTEND, AttendanceStatus.ABSENT);
	}

	@Test
	@DisplayName("확정 계산 뒤에 벌칙 면제가 먼저 커밋되면 확정은 충돌로 실패하고 리비전 없이 면제만 남는다")
	void waiverBeatsConfirm() {
		Long gameId = revealedMoneyGame();
		AtomicBoolean competitorRan = new AtomicBoolean();
		doAnswer(invocation -> {
			Object input = invocation.callRealMethod();
			if (competitorRan.compareAndSet(false, true)) {
				// 확정이 정산 version을 읽고 계산까지 마친 사이에 면제가 먼저 커밋된다
				CompletableFuture.runAsync(() -> penaltyConditionService.waive(gameId, host.getId()))
						.orTimeout(30, TimeUnit.SECONDS).join();
			}
			return input;
		}).when(inputAssembler).assemble(eq(meeting.getId()), any(MeetingPenalties.class));

		assertThatThrownBy(() -> confirmService.confirm(meeting.getId(), host.getId(), true))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(revisionCount()).isZero();
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(jdbcTemplate.queryForList("select status from penalty where game_session_id = ?",
				String.class, gameId)).containsOnly("WAIVED");
	}

	@RepeatedTest(5)
	@DisplayName("대기 중 금액형 게임 취소와 확정이 동시에 오면 취소는 항상 성공하고, 확정은 취소가 먼저일 때만 리비전을 남긴다")
	void confirmVersusMoneyGameCancel() {
		Long gameId = waitingMoneyGame();

		List<Outcome<Object>> outcomes = ConcurrentRunner.runTogether(List.<Callable<Object>>of(
				() -> confirmService.confirm(meeting.getId(), host.getId(), true),
				() -> gameLobbyService.cancel(gameId, host.getId())));

		Outcome<Object> confirm = outcomes.get(0);
		assertThat(outcomes.get(1).failure()).isNull();
		assertThat(gameStatus(gameId)).isEqualTo(GameStatus.CANCELED.name());
		if (confirm.succeeded()) {
			assertThat(((SettlementResponse) confirm.value()).status()).isEqualTo(SettlementStatus.CONFIRMED);
			assertThat(revisionCount()).isEqualTo(1);
		} else {
			assertThat(isConfirmConflict(confirm.failure())).as("확정 실패 원인: %s", confirm.failure()).isTrue();
			assertThat(revisionCount()).isZero();
			assertThat(settlementStatus()).isEqualTo(SettlementStatus.DRAFT);
		}
	}

	private boolean isConfirmConflict(Throwable failure) {
		return failure instanceof ObjectOptimisticLockingFailureException
				|| (failure instanceof BusinessException business
						&& business.getErrorCode() == ErrorCode.MONEY_GAME_IN_PROGRESS);
	}

	private Long waitingMoneyGame() {
		return gameLobbyService.create(meeting.getId(), host.getId(), new GameCreateRequest(GameType.LADDER,
				PenaltyKind.MONEY, 10_000L, null, List.of(host.getId(), other.getId()), null)).gameId();
	}

	private Long revealedMoneyGame() {
		Long gameId = waitingMoneyGame();
		gameLobbyService.start(gameId, host.getId());
		return gameId;
	}

	private MemberAttendanceResponse respond(Member member, AttendanceStatus status) {
		return attendanceService.respondMine(meeting.getId(), member.getId(),
				new AttendanceRespondRequest(null, List.of(new RoundAttendanceItem(round.getId(), status))));
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Member member) {
		return GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER);
	}

	private Attendance attend(Member member) {
		return Attendance.respond(round, member, AttendanceStatus.ATTEND, START_AT);
	}

	private SettlementStatus settlementStatus() {
		return settlementRepository.findByMeetingId(meeting.getId()).orElseThrow().getStatus();
	}

	private String gameStatus(Long gameId) {
		return jdbcTemplate.queryForObject("select status from game_session where id = ?", String.class, gameId);
	}

	private int revisionCount() {
		return count("select count(*) from settlement_revision r join settlement s on r.settlement_id = s.id "
				+ "where s.meeting_id = ?", meeting.getId());
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
