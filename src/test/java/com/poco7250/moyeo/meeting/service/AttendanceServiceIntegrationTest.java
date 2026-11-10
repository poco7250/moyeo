package com.poco7250.moyeo.meeting.service;

import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ABSENT;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ATTEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceShortcut;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.AttendanceSettlementHandler;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementReopenedEvent;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.test.util.AopTestUtils;

/**
 * 재시도 실행기는 활성 트랜잭션 안에서 호출을 거부하고 커밋 후 이벤트도 봐야 해서, 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AttendanceServiceIntegrationTest {

	@Autowired
	AttendanceService attendanceService;

	@Autowired
	SettlementConfirmService confirmService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

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
	ChangeLogRepository changeLogRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	@MockitoSpyBean
	AttendanceSettlementHandler attendanceSettlementHandler;

	private Group group;
	private Member host;
	private Member other;
	private List<GroupMember> memberships;
	private Meeting meeting;
	private Round roundWithExpense;
	private Round roundWithoutExpense;
	private Settlement settlement;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("철수");
		other = saveMember("지수");
		memberships = groupMemberRepository.saveAll(List.of(join(host), join(other)));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		roundWithExpense = roundRepository.save(Round.create(meeting, 1, "밥"));
		roundWithoutExpense = roundRepository.save(Round.create(meeting, 2, "볼링"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		expenseRepository.save(Expense.create(roundWithExpense, host, 90_000L, "밥"));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		// 스냅샷·ChangeLog는 저장소로 지우지 않는 append-only 데이터라 SQL로 지운다
		String revisionIds = "(select id from settlement_revision where settlement_id = ?)";
		jdbcTemplate.update("delete from transfer where revision_id in " + revisionIds, settlement.getId());
		jdbcTemplate.update("delete from revision_member_share where revision_id in " + revisionIds,
				settlement.getId());
		jdbcTemplate.update("delete from settlement_revision where settlement_id = ?", settlement.getId());
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		jdbcTemplate.update("delete from expense where round_id = ?", roundWithExpense.getId());
		jdbcTemplate.update("delete from attendance where round_id in (?, ?)", roundWithExpense.getId(),
				roundWithoutExpense.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteAllById(List.of(roundWithExpense.getId(), roundWithoutExpense.getId()));
		meetingRepository.deleteById(meeting.getId());
		groupMemberRepository.deleteAll(memberships);
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAll(List.of(host, other));
	}

	@Test
	@DisplayName("늦참으로 응답한 뒤 1차만으로 다시 응답하면 차수당 1건이 유지되고, 확정 전이라 변경 로그는 없다")
	void reRespondBeforeConfirm() {
		attendanceService.respondMine(meeting.getId(), other.getId(),
				new AttendanceRespondRequest(AttendanceShortcut.LATE_FROM_SECOND, null));
		assertThat(statuses(other)).containsExactly(ABSENT, ATTEND);

		attendanceService.respondMine(meeting.getId(), other.getId(),
				new AttendanceRespondRequest(AttendanceShortcut.FIRST_ONLY, null));

		assertThat(statuses(other)).containsExactly(ATTEND, ABSENT);
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(logs()).isEmpty();
	}

	@Test
	@DisplayName("확정 후 지출 있는 차수의 참석을 바꾸면 REOPENED, 변경 로그 1건, 커밋 후 재확정 필요 이벤트 1회다")
	void attendanceChangeAfterConfirmReopens() {
		confirmWithAttendees();
		Attendance attendance = attendanceOf(other, roundWithExpense);

		respond(other, roundWithExpense, ABSENT);

		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(logs()).singleElement().satisfies(log -> {
			assertThat(log.getActorId()).isEqualTo(other.getId());
			assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.ATTENDANCE);
			assertThat(log.getEntityId()).isEqualTo(attendance.getId());
			assertThat(log.getChangeType()).isEqualTo(ChangeType.UPDATE);
			assertThat(log.getBeforeJson()).contains("\"status\":\"ATTEND\"");
			assertThat(log.getAfterJson()).contains("\"status\":\"ABSENT\"");
			assertThat(log.getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
		});
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(SettlementReopenedEvent.TYPE, new SettlementReopenedResponse(other.getId(),
						List.of(new SettlementChangeSummary(ChangeLogEntityType.ATTENDANCE, attendance.getId(),
								ChangeType.UPDATE)))));
	}

	@Test
	@DisplayName("확정 후 지출 없는 차수의 참석을 바꾸면 정산 상태·버전이 그대로이고 변경 로그도 없다")
	void attendanceChangeOnRoundWithoutExpense() {
		confirmWithAttendees();
		Long versionBefore = currentSettlement().getVersion();

		respond(other, roundWithoutExpense, ATTEND);

		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(currentSettlement().getVersion()).isEqualTo(versionBefore);
		assertThat(logs()).isEmpty();
	}

	@Test
	@DisplayName("확정 후 같은 값으로 다시 응답하면 정산 상태·버전이 그대로이고 변경 로그도 없다")
	void sameAnswerAfterConfirm() {
		confirmWithAttendees();
		Long versionBefore = currentSettlement().getVersion();

		respond(other, roundWithExpense, ATTEND);

		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(currentSettlement().getVersion()).isEqualTo(versionBefore);
		assertThat(logs()).isEmpty();
	}

	@Test
	@DisplayName("다른 멤버의 응답이 먼저 커밋돼 낙관적 락이 충돌해도 재시도로 둘 다 저장된다")
	void conflictingResponsesBothSucceedByRetry() {
		respond(host, roundWithExpense, ATTEND);
		respond(other, roundWithExpense, ATTEND);
		// 트랜잭션 프록시를 거치면 스터빙·검증 호출이 MANDATORY 검사에 걸려서 안쪽 스파이에 직접 건다
		AttendanceSettlementHandler handlerSpy = AopTestUtils.getUltimateTargetObject(attendanceSettlementHandler);
		clearInvocations(handlerSpy);
		AtomicBoolean competitorRan = new AtomicBoolean();
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (competitorRan.compareAndSet(false, true)) {
				// 이 트랜잭션이 정산 version을 읽은 뒤 다른 멤버의 응답이 먼저 커밋되게 한다
				CompletableFuture.runAsync(() -> respond(other, roundWithExpense, ABSENT)).join();
			}
			return result;
		}).when(handlerSpy).onChanged(eq(meeting.getId()), any(), any());

		respond(host, roundWithExpense, ABSENT);

		assertThat(attendanceOf(host, roundWithExpense).getStatus()).isEqualTo(ABSENT);
		assertThat(attendanceOf(other, roundWithExpense).getStatus()).isEqualTo(ABSENT);
		// 먼저 시작한 쪽 1회 + 끼어든 쪽 1회 + 재시도 1회
		verify(handlerSpy, times(3)).onChanged(eq(meeting.getId()), any(), any());
	}

	private void confirmWithAttendees() {
		respond(host, roundWithExpense, ATTEND);
		respond(other, roundWithExpense, ATTEND);
		confirmService.confirm(meeting.getId(), host.getId(), false);
		clearInvocations(messagingTemplate);
	}

	private void respond(Member member, Round round, AttendanceStatus status) {
		attendanceService.respondMine(meeting.getId(), member.getId(), new AttendanceRespondRequest(null,
				List.of(new RoundAttendanceItem(round.getId(), status))));
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Member member) {
		return GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER);
	}

	private Settlement currentSettlement() {
		return settlementRepository.findById(settlement.getId()).orElseThrow();
	}

	private List<ChangeLog> logs() {
		return changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
	}

	private Attendance attendanceOf(Member member, Round round) {
		return attendanceRepository.findAllByMeetingIdAndMemberId(meeting.getId(), member.getId()).stream()
				.filter(attendance -> attendance.getRound().getId().equals(round.getId()))
				.findFirst()
				.orElseThrow();
	}

	private List<AttendanceStatus> statuses(Member member) {
		return attendanceRepository.findAllByMeetingIdAndMemberId(meeting.getId(), member.getId()).stream()
				.sorted(Comparator.comparingLong(attendance -> attendance.getRound().getId()))
				.map(Attendance::getStatus)
				.toList();
	}
}
