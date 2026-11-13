package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
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
 * 실제 커밋 순서와 잠금 충돌을 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementConfirmIntegrationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2099, 12, 20, 18, 0);

	@Autowired
	SettlementConfirmService confirmService;

	@Autowired
	SettlementQueryService queryService;

	@Autowired
	ExpenseService expenseService;

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
	JdbcTemplate jdbcTemplate;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@MockitoSpyBean
	SettlementInputAssembler inputAssembler;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Member host;
	private Member other;
	private Member third;
	private List<GroupMember> memberships;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;
	private Expense expense;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("철수");
		other = saveMember("지수");
		third = saveMember("민수");
		memberships = groupMemberRepository.saveAll(List.of(join(host), join(other), join(third)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", NOW, null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		attendanceRepository.saveAll(List.of(attend(host), attend(other), attend(third)));
		expense = expenseRepository.save(Expense.create(round, host, 90_000L, "볼링"));
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
		memberRepository.deleteAll(List.of(host, other, third));
	}

	@Test
	@DisplayName("미리보기는 저장하지 않고, 확정하면 리비전 1이 고정되어 이후 지출을 고쳐도 REOPENED 조회는 그 금액을 유지한다")
	void confirmFreezesRevision() {
		SettlementResponse preview = queryService.get(meeting.getId(), other.getId());
		assertThat(preview.preview()).isTrue();
		assertThat(preview.basisRevisionNo()).isNull();
		assertThat(revisionCount()).isZero();

		SettlementResponse confirmed = confirmService.confirm(meeting.getId(), host.getId(), false);

		List<SettlementMemberResponse> expectedMembers = List.of(
				new SettlementMemberResponse(host.getId(), 30_000L, 60_000L),
				new SettlementMemberResponse(other.getId(), 30_000L, -30_000L),
				new SettlementMemberResponse(third.getId(), 30_000L, -30_000L));
		List<SettlementTransferResponse> expectedTransfers = List.of(
				new SettlementTransferResponse(other.getId(), host.getId(), 30_000L),
				new SettlementTransferResponse(third.getId(), host.getId(), 30_000L));
		assertThat(confirmed).isEqualTo(new SettlementResponse(
				SettlementStatus.CONFIRMED, 1, false, expectedMembers, expectedTransfers, List.of(), List.of()));
		assertThat(revisionCount()).isEqualTo(1);
		assertThat(queryService.get(meeting.getId(), other.getId())).isEqualTo(confirmed);
		assertThat(currentSettlement().getFirstConfirmedAt()).isNotNull();

		expenseService.update(expense.getId(), other.getId(), new ExpenseRequest(30_000L, host.getId(), "볼링"));

		assertThat(queryService.get(meeting.getId(), other.getId())).isEqualTo(new SettlementResponse(
				SettlementStatus.REOPENED, 1, false, expectedMembers, expectedTransfers, List.of(), List.of()));
	}

	@Test
	@DisplayName("리비전 1 확정 뒤 멤버가 지출 금액을 고치면 REOPENED, 변경 로그 저장, 커밋 후 재확정 필요 이벤트 1회다")
	void expenseUpdateAfterConfirmReopens() {
		confirmService.confirm(meeting.getId(), host.getId(), false);

		expenseService.update(expense.getId(), other.getId(), new ExpenseRequest(30_000L, host.getId(), "볼링"));

		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId()))
				.singleElement().satisfies(log -> {
					assertThat(log.getActorId()).isEqualTo(other.getId());
					assertThat(log.getChangedAt()).isNotNull();
					assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.EXPENSE);
					assertThat(log.getEntityId()).isEqualTo(expense.getId());
					assertThat(log.getChangeType()).isEqualTo(ChangeType.UPDATE);
					assertThat(log.getBeforeJson()).contains("\"amount\":90000");
					assertThat(log.getAfterJson()).contains("\"amount\":30000");
				});
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(SettlementReopenedEvent.TYPE, new SettlementReopenedResponse(other.getId(),
						List.of(new SettlementChangeSummary(ChangeLogEntityType.EXPENSE, expense.getId(),
								ChangeType.UPDATE)))));
	}

	@Test
	@DisplayName("상태 검사를 함께 통과한 확정 2건이 겹치면 한 건만 성공하고 늦은 쪽은 낙관적 락 충돌이다")
	void concurrentConfirm() {
		CyclicBarrier bothPassedStatusCheck = new CyclicBarrier(2);
		doAnswer(invocation -> {
			Object input = invocation.callRealMethod();
			bothPassedStatusCheck.await(10, TimeUnit.SECONDS);
			return input;
		}).when(inputAssembler).assemble(eq(meeting.getId()), any(MeetingPenalties.class));

		List<Throwable> failures = List.of(confirmAsync(), confirmAsync()).stream()
				.map(CompletableFuture::join)
				.filter(Objects::nonNull)
				.toList();

		assertThat(failures).singleElement().isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(revisionCount()).isEqualTo(1);
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
	}

	@Test
	@DisplayName("확정 계산 뒤에 지출 수정이 먼저 커밋되면 확정은 충돌로 실패하고 DRAFT와 수정값이 남는다")
	void expenseUpdateBeatsConfirm() {
		doAnswer(invocation -> {
			Object input = invocation.callRealMethod();
			CompletableFuture.runAsync(() -> expenseService.update(expense.getId(), other.getId(),
					new ExpenseRequest(30_000L, host.getId(), "볼링"))).join();
			return input;
		}).when(inputAssembler).assemble(eq(meeting.getId()), any(MeetingPenalties.class));

		assertThatThrownBy(() -> confirmService.confirm(meeting.getId(), host.getId(), false))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(revisionCount()).isZero();
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(expenseRepository.findById(expense.getId()).orElseThrow().getAmount()).isEqualTo(30_000L);
	}

	private CompletableFuture<Throwable> confirmAsync() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				confirmService.confirm(meeting.getId(), host.getId(), false);
				return null;
			} catch (RuntimeException exception) {
				return exception;
			}
		});
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Member member) {
		return GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER);
	}

	private Attendance attend(Member member) {
		return Attendance.respond(round, member, AttendanceStatus.ATTEND, NOW);
	}

	private Settlement currentSettlement() {
		return settlementRepository.findById(settlement.getId()).orElseThrow();
	}

	private int revisionCount() {
		return jdbcTemplate.queryForObject("select count(*) from settlement_revision where settlement_id = ?",
				Integer.class, settlement.getId());
	}
}
