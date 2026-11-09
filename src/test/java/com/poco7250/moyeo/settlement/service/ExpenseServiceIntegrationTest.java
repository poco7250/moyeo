package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
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
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareAdjustRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseShareItem;
import com.poco7250.moyeo.settlement.dto.ExpenseShareResponse;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 커밋 후 이벤트와 flush 순서를 실제로 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpenseServiceIntegrationTest {

	@Autowired
	ExpenseService expenseService;

	@Autowired
	ExpenseShareService expenseShareService;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

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

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Member payer;
	private Member other;
	private Member third;
	private List<GroupMember> memberships;
	private Meeting meeting;
	private Round round;
	private Settlement settlement;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		payer = saveMember("철수");
		other = saveMember("지수");
		third = saveMember("민수");
		memberships = groupMemberRepository.saveAll(List.of(join(payer), join(other), join(third)));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, payer.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		// ChangeLog는 append-only라 저장소에 삭제 경로가 없어 SQL로 지운다
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		jdbcTemplate.update("delete from expense_share where expense_id in "
				+ "(select id from expense where round_id = ?)", round.getId());
		jdbcTemplate.update("delete from expense where round_id = ?", round.getId());
		settlementRepository.deleteById(settlement.getId());
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meeting.getId());
		groupMemberRepository.deleteAll(memberships);
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAll(List.of(payer, other, third));
	}

	@Test
	@DisplayName("CONFIRMED 정산에 지출을 추가하면 REOPENED·생성 로그 1건·커밋 후 이벤트 1회다")
	void createReopensConfirmed() {
		confirmSettlement();

		ExpenseResponse response = expenseService.create(round.getId(), other.getId(),
				new ExpenseRequest(100_000L, payer.getId(), "볼링"));

		assertThat(expenseRepository.findById(response.id())).isPresent();
		assertReopenedOnceBy(other.getId());
		ChangeLog log = singleLog();
		assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.EXPENSE);
		assertThat(log.getEntityId()).isEqualTo(response.id());
		assertThat(log.getChangeType()).isEqualTo(ChangeType.CREATE);
		assertThat(log.getActorId()).isEqualTo(other.getId());
		assertThat(log.getBeforeJson()).isNull();
		assertThat(log.getAfterJson()).contains("\"amount\":100000", "\"memo\":\"볼링\"");
		assertThat(log.getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("CONFIRMED 정산의 지출을 수정하면 REOPENED이고 로그에 전/후 금액이 남는다")
	void updateReopensConfirmed() {
		Expense expense = saveExpense();
		confirmSettlement();

		ExpenseResponse response = expenseService.update(expense.getId(), other.getId(),
				new ExpenseRequest(70_000L, other.getId(), "뒤풀이"));

		assertThat(response.amount()).isEqualTo(70_000L);
		assertThat(response.payerId()).isEqualTo(other.getId());
		assertReopenedOnceBy(other.getId());
		ChangeLog log = singleLog();
		assertThat(log.getChangeType()).isEqualTo(ChangeType.UPDATE);
		assertThat(log.getBeforeJson()).contains("\"amount\":100000");
		assertThat(log.getAfterJson()).contains("\"amount\":70000");
	}

	@Test
	@DisplayName("지출을 삭제하면 조정도 지워지고 REOPENED·삭제 전 조정이 담긴 로그가 남는다")
	void deleteReopensConfirmed() {
		Expense expense = saveExpense();
		expenseShareService.adjust(expense.getId(), payer.getId(), new ExpenseShareAdjustRequest(List.of(
				new ExpenseShareItem(other.getId(), null, null, true))));
		confirmSettlement();

		expenseService.delete(expense.getId(), other.getId());

		assertThat(expenseRepository.findById(expense.getId())).isEmpty();
		assertThat(shareCount(expense.getId())).isZero();
		assertReopenedOnceBy(other.getId());
		List<ChangeLog> logs = logs();
		assertThat(logs).hasSize(2);
		ChangeLog deleted = logs.getLast();
		assertThat(deleted.getChangeType()).isEqualTo(ChangeType.DELETE);
		assertThat(deleted.getEntityId()).isEqualTo(expense.getId());
		assertThat(deleted.getBeforeJson()).contains("\"memberId\":" + other.getId(), "\"excluded\":true");
		assertThat(deleted.getAfterJson()).isNull();
	}

	@Test
	@DisplayName("같은 회원을 포함해 조정을 다시 보내도 유니크 위반 없이 기존 행을 고치고 빠진 회원은 지운다")
	void adjustReplacesWithoutUniqueViolation() {
		Expense expense = saveExpense();
		expenseShareService.adjust(expense.getId(), payer.getId(), new ExpenseShareAdjustRequest(List.of(
				new ExpenseShareItem(other.getId(), null, null, true),
				new ExpenseShareItem(third.getId(), null, 2, null))));
		Long keptShareId = shareId(expense.getId(), other.getId());

		ExpenseResponse response = expenseShareService.adjust(expense.getId(), payer.getId(),
				new ExpenseShareAdjustRequest(List.of(
						new ExpenseShareItem(other.getId(), 5_000L, null, null),
						new ExpenseShareItem(payer.getId(), null, 3, null))));

		assertThat(response.shares()).containsExactlyInAnyOrder(
				new ExpenseShareResponse(other.getId(), 5_000L, null, false),
				new ExpenseShareResponse(payer.getId(), null, 3, false));
		assertThat(shareCount(expense.getId())).isEqualTo(2);
		assertThat(shareId(expense.getId(), other.getId())).isEqualTo(keptShareId);
		assertThat(logs()).hasSize(2).allSatisfy(log -> {
			assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.EXPENSE_SHARE);
			assertThat(log.getEntityId()).isEqualTo(expense.getId());
			assertThat(log.getChangeType()).isEqualTo(ChangeType.UPDATE);
		});
	}

	@Test
	@DisplayName("CONFIRMED 정산의 조정을 바꾸면 REOPENED·이벤트 1회다")
	void adjustReopensConfirmed() {
		Expense expense = saveExpense();
		confirmSettlement();

		expenseShareService.adjust(expense.getId(), other.getId(), new ExpenseShareAdjustRequest(List.of(
				new ExpenseShareItem(third.getId(), null, null, true))));

		assertReopenedOnceBy(other.getId());
		assertThat(singleLog().getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("DRAFT 정산은 지출을 추가해도 DRAFT이고 로그는 남지만 이벤트는 없다")
	void draftStaysDraft() {
		expenseService.create(round.getId(), payer.getId(), new ExpenseRequest(1_000L, payer.getId(), null));

		assertThat(currentStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(singleLog().getSettlementStatusAfter()).isEqualTo(SettlementStatus.DRAFT);
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("목록은 조정이 여러 건이어도 지출을 한 번씩만 돌려준다")
	void listDeduplicates() {
		Expense expense = saveExpense();
		expenseShareService.adjust(expense.getId(), payer.getId(), new ExpenseShareAdjustRequest(List.of(
				new ExpenseShareItem(other.getId(), null, null, true),
				new ExpenseShareItem(third.getId(), null, 2, null))));

		assertThat(expenseService.list(meeting.getId(), payer.getId())).singleElement()
				.satisfies(response -> assertThat(response.shares()).hasSize(2));
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Member member) {
		return GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER);
	}

	private Expense saveExpense() {
		return expenseRepository.save(Expense.create(round, payer, 100_000L, "볼링"));
	}

	private void confirmSettlement() {
		Settlement current = settlementRepository.findById(settlement.getId()).orElseThrow();
		ReflectionTestUtils.setField(current, "status", SettlementStatus.CONFIRMED);
		settlementRepository.save(current);
	}

	private SettlementStatus currentStatus() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getStatus();
	}

	private void assertReopenedOnceBy(Long actorId) {
		assertThat(currentStatus()).isEqualTo(SettlementStatus.REOPENED);
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(SettlementReopenedEvent.TYPE, new SettlementReopenedResponse(actorId)));
	}

	private List<ChangeLog> logs() {
		return changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
	}

	private ChangeLog singleLog() {
		List<ChangeLog> logs = logs();
		assertThat(logs).hasSize(1);
		return logs.getFirst();
	}

	private int shareCount(Long expenseId) {
		return jdbcTemplate.queryForObject("select count(*) from expense_share where expense_id = ?", Integer.class,
				expenseId);
	}

	private Long shareId(Long expenseId, Long memberId) {
		return jdbcTemplate.queryForObject("select id from expense_share where expense_id = ? and member_id = ?",
				Long.class, expenseId, memberId);
	}
}
