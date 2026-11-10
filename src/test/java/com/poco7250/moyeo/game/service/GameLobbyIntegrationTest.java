package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameResultRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
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
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 커밋과 version 충돌을 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GameLobbyIntegrationTest {

	private static long nextKakaoId = 900_000L;

	@Autowired
	GameLobbyService gameLobbyService;

	@Autowired
	GameSessionRepository gameSessionRepository;

	@Autowired
	GameResultRepository gameResultRepository;

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
	JdbcTemplate jdbcTemplate;

	@Autowired
	TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;
	private Member host;
	private Member second;
	private Member third;
	private Member absent;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("호스트");
		second = saveMember("민수");
		third = saveMember("영희");
		absent = saveMember("불참자");
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		respond(host, AttendanceStatus.ATTEND);
		respond(second, AttendanceStatus.ATTEND);
		respond(third, AttendanceStatus.ATTEND);
		respond(absent, AttendanceStatus.ABSENT);
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		Long meetingId = meeting.getId();
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meetingId);
		String gameIds = "select id from game_session where round_id = " + round.getId();
		jdbcTemplate.update("delete from game_result where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_participant where game_session_id in (" + gameIds + ")");
		jdbcTemplate.update("delete from game_session where round_id = ?", round.getId());
		jdbcTemplate.update("delete from attendance where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meetingId);
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), second.getId(), third.getId(), absent.getId()));
	}

	@Test
	@DisplayName("참석자 일부로 만들고 시작하면 벌칙자가 정해져 게임·모임 토픽으로 나간다")
	void createAndStart() {
		GameStateResponse created = createGame(PenaltyKind.MONEY, List.of(second.getId(), third.getId()));
		assertThat(created.status()).isEqualTo(GameStatus.WAITING);
		assertThat(created.result()).isNull();
		assertThat(created.participants()).hasSize(2);

		GameStateResponse started = gameLobbyService.start(created.gameId(), host.getId());

		assertThat(started.status()).isEqualTo(GameStatus.REVEALED);
		assertThat(started.result().penaltyMemberIds()).hasSize(1)
				.isSubsetOf(second.getId(), third.getId());
		RealtimeMessage message = new RealtimeMessage(GameStateChangedEvent.TYPE, started);
		verify(messagingTemplate).convertAndSend("/topic/games/" + created.gameId(), (Object) message);
		verify(messagingTemplate).convertAndSend("/topic/meetings/" + meeting.getId(), (Object) message);
	}

	@Test
	@DisplayName("금액형 게임 취소는 정산 version만 올리고 변경 로그를 남기며 REOPENED로 바꾸지 않는다")
	void cancelMoneyGame() {
		confirmSettlement();
		Long versionBefore = currentSettlement().getVersion();
		GameStateResponse created = createGame(PenaltyKind.MONEY, List.of(second.getId(), third.getId()));

		GameStateResponse canceled = gameLobbyService.cancel(created.gameId(), host.getId());

		assertThat(canceled.status()).isEqualTo(GameStatus.CANCELED);
		Settlement after = currentSettlement();
		assertThat(after.getVersion()).isGreaterThan(versionBefore);
		assertThat(after.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).hasSize(1);
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/games/" + created.gameId()),
				(Object) argThat(payload -> payload instanceof RealtimeMessage));
	}

	@RepeatedTest(5)
	@DisplayName("시작 요청이 동시에 와도 추첨은 한 번만 일어나고 데드락이 없다")
	void concurrentStart() {
		GameStateResponse created = createGame(PenaltyKind.NONE, List.of(host.getId(), second.getId(), third.getId()));
		CountDownLatch ready = new CountDownLatch(2);

		List<CompletableFuture<Throwable>> starts = List.of(startAsync(created.gameId(), ready),
				startAsync(created.gameId(), ready));

		List<Throwable> failures = starts.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
		assertThat(failures).hasSize(1);
		assertThat(failures.getFirst()).isInstanceOfAny(ObjectOptimisticLockingFailureException.class,
				BusinessException.class);
		assertThat(gameResultRepository.findAllByGameSessionIdAndOutcome(created.gameId(), GameResult.OUTCOME_PENALTY))
				.hasSize(1);
		assertThat(gameResultRepository.findAllByGameSessionIdAndOutcome(created.gameId(), GameResult.OUTCOME_SAFE))
				.hasSize(2);
	}

	@Test
	@DisplayName("참석자 조회는 불참자와 다른 차수 응답을 제외한다")
	void findAttendingMembers() {
		Round otherRound = roundRepository.save(Round.create(meeting, 2, "노래방"));
		saveAttendance(otherRound, absent, AttendanceStatus.ATTEND);

		List<Member> members = attendanceRepository.findAttendingMembers(round.getId(),
				List.of(second.getId(), absent.getId()));

		assertThat(members).extracting(Member::getId).containsExactly(second.getId());
		jdbcTemplate.update("delete from attendance where round_id = ?", otherRound.getId());
		roundRepository.deleteById(otherRound.getId());
	}

	private CompletableFuture<Throwable> startAsync(Long gameId, CountDownLatch ready) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				ready.countDown();
				ready.await();
				gameLobbyService.start(gameId, host.getId());
				return null;
			} catch (RuntimeException e) {
				return e;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return e;
			}
		});
	}

	private GameStateResponse createGame(PenaltyKind penaltyKind, List<Long> memberIds) {
		return gameLobbyService.create(round.getId(), host.getId(),
				new GameCreateRequest(GameType.LADDER, penaltyKind, null, memberIds, null));
	}

	private Member saveMember(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}

	private void respond(Member member, AttendanceStatus status) {
		saveAttendance(round, member, status);
	}

	private void saveAttendance(Round target, Member member, AttendanceStatus status) {
		attendanceRepository.save(Attendance.respond(target, member, status, LocalDateTime.of(2026, 12, 1, 12, 0)));
	}

	private void confirmSettlement() {
		jdbcTemplate.update("update settlement set status = 'CONFIRMED' where id = ?", settlement.getId());
	}

	private Settlement currentSettlement() {
		return settlementRepository.findById(settlement.getId()).orElseThrow();
	}
}
