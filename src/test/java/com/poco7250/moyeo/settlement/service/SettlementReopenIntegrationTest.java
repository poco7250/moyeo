package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
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
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 정산 확정 되돌리기(Task 075) 통합 테스트. 커밋 후 이벤트와 잠금 충돌을 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementReopenIntegrationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2099, 12, 20, 18, 0);

	@Autowired
	SettlementReopenService reopenService;

	@Autowired
	SettlementConfirmService confirmService;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	MeetingAccessChecker meetingAccessChecker;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Member host;
	private Member treasurer;
	private Member owner;
	private Member member;
	private List<GroupMember> memberships;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("철수");
		treasurer = saveMember("지수");
		owner = saveMember("민수");
		member = saveMember("영희");
		memberships = groupMemberRepository.saveAll(List.of(join(host, GroupRole.MEMBER),
				join(treasurer, GroupRole.TREASURER), join(owner, GroupRole.OWNER), join(member, GroupRole.MEMBER)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", NOW, null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		attendanceRepository.saveAll(List.of(attend(host), attend(treasurer), attend(owner), attend(member)));
		expenseRepository.save(Expense.create(round, host, 80_000L, "볼링"));
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
		jdbcTemplate.update("delete from expense where round_id = ?", round.getId());
		jdbcTemplate.update("delete from attendance where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meeting.getId());
		groupMemberRepository.deleteAll(memberships);
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAll(List.of(host, treasurer, owner, member));
	}

	@Test
	@DisplayName("호스트가 되돌리면 REOPENED·리비전 1 유지·변경 로그·재확정 필요 이벤트가 남고, 재확정하면 리비전 2에 변동액 0이다")
	void hostReopensThenReconfirms() {
		SettlementResponse confirmed = confirmService.confirm(meeting.getId(), host.getId(), false);

		SettlementResponse reopened = reopenService.reopen(meeting.getId(), host.getId());

		assertThat(reopened.status()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(reopened.basisRevisionNo()).isEqualTo(1);
		assertThat(reopened.transfers()).isEqualTo(confirmed.transfers());
		assertThat(revisionCount()).isEqualTo(1);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId()))
				.singleElement().satisfies(log -> {
					assertThat(log.getActorId()).isEqualTo(host.getId());
					assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.SETTLEMENT);
					assertThat(log.getEntityId()).isEqualTo(settlement.getId());
					assertThat(log.getChangeType()).isEqualTo(ChangeType.UPDATE);
					assertThat(log.getBeforeJson()).contains("\"status\":\"CONFIRMED\"", "\"revisionNo\":1");
					assertThat(log.getAfterJson()).contains("\"status\":\"REOPENED\"", "\"revisionNo\":1");
					assertThat(log.getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
				});
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(SettlementReopenedEvent.TYPE, new SettlementReopenedResponse(host.getId(),
						List.of(new SettlementChangeSummary(ChangeLogEntityType.SETTLEMENT, settlement.getId(),
								ChangeType.UPDATE)))));

		SettlementResponse reconfirmed = confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(reconfirmed.basisRevisionNo()).isEqualTo(2);
		assertThat(reconfirmed.revisionChange().previousRevisionNo()).isEqualTo(1);
		assertThat(reconfirmed.revisionChange().changes())
				.allSatisfy(change -> assertThat(change.delta()).isZero());
	}

	@Test
	@DisplayName("방장은 남의 모임 정산도 되돌릴 수 있다")
	void ownerReopens() {
		confirmService.confirm(meeting.getId(), host.getId(), false);

		reopenService.reopen(meeting.getId(), owner.getId());

		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("총무는 확정할 수 있어도 되돌릴 수 없고, 일반 멤버도 403이며 정산은 CONFIRMED로 남는다")
	void treasurerAndMemberRejected() {
		confirmService.confirm(meeting.getId(), treasurer.getId(), false);

		for (Member actor : List.of(treasurer, member)) {
			assertThatThrownBy(() -> reopenService.reopen(meeting.getId(), actor.getId()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		}
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).isEmpty();
	}

	@Test
	@DisplayName("DRAFT·REOPENED 정산은 되돌릴 수 없다")
	void notConfirmedRejected() {
		assertThatThrownBy(() -> reopenService.reopen(meeting.getId(), host.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SETTLEMENT_NOT_CONFIRMED);

		confirmService.confirm(meeting.getId(), host.getId(), false);
		reopenService.reopen(meeting.getId(), host.getId());

		assertThatThrownBy(() -> reopenService.reopen(meeting.getId(), host.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SETTLEMENT_NOT_CONFIRMED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).hasSize(1);
	}

	@Test
	@DisplayName("되돌리기 2건이 동시에 오면 한 건만 성공하고 늦은 쪽은 버전 충돌 또는 SETTLEMENT_NOT_CONFIRMED, 변경 로그는 1건이다")
	void concurrentReopen() {
		confirmService.confirm(meeting.getId(), host.getId(), false);
		// 먼저 온 쪽의 정산 UPDATE가 행 잠금을 잡아 늦은 쪽을 세우므로, 잠금 전(권한 검사 직후)에 두 요청을 만나게 한다.
		// 늦은 쪽은 먼저 온 쪽 커밋 전에 읽었으면 버전 충돌, 커밋 뒤에 읽었으면 상태 검사에서 거절된다
		CyclicBarrier bothPassedAccessCheck = new CyclicBarrier(2);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			bothPassedAccessCheck.await(10, TimeUnit.SECONDS);
			return result;
		}).when(meetingAccessChecker).requireHost(eq(meeting.getId()), any());

		List<Throwable> failures = List.of(reopenAsync(host), reopenAsync(owner)).stream()
				.map(CompletableFuture::join)
				.filter(Objects::nonNull)
				.toList();

		assertThat(failures).singleElement().satisfies(failure -> assertThat(
				failure instanceof ObjectOptimisticLockingFailureException
						|| failure instanceof BusinessException business
						&& business.getErrorCode() == ErrorCode.SETTLEMENT_NOT_CONFIRMED)
				.as("늦은 쪽 실패: %s", failure).isTrue());
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).hasSize(1);
	}

	private CompletableFuture<Throwable> reopenAsync(Member actor) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				reopenService.reopen(meeting.getId(), actor.getId());
				return null;
			} catch (RuntimeException exception) {
				return exception;
			}
		});
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Member target, GroupRole role) {
		return GroupMember.join(group, target, target.getNickname(), role);
	}

	private Attendance attend(Member target) {
		return Attendance.respond(round, target, AttendanceStatus.ATTEND, NOW);
	}

	private Settlement currentSettlement() {
		return settlementRepository.findById(settlement.getId()).orElseThrow();
	}

	private int revisionCount() {
		return jdbcTemplate.queryForObject("select count(*) from settlement_revision where settlement_id = ?",
				Integer.class, settlement.getId());
	}
}
