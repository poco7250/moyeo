package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareSnapshot;
import com.poco7250.moyeo.settlement.dto.ExpenseSnapshot;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 2L;
	private static final Long ROUND_ID = 3L;
	private static final Long EXPENSE_ID = 4L;
	private static final Long ACTOR_ID = 7L;
	private static final Long PAYER_ID = 8L;
	private static final Long NEW_PAYER_ID = 9L;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	ExpenseLoader expenseLoader;

	@Mock
	ExpenseRepository expenseRepository;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@InjectMocks
	ExpenseService expenseService;

	private Group group;
	private Round round;
	private Member payer;

	@BeforeEach
	void setUp() {
		group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		Meeting meeting = Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, ACTOR_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		round = Round.create(meeting, 1, "볼링");
		ReflectionTestUtils.setField(round, "id", ROUND_ID);
		payer = member(PAYER_ID);
	}

	@Test
	@DisplayName("등록하면 지출을 저장하고 직렬화·REOPENED 처리 뒤 생성 로그를 1건 남긴다")
	void create() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, ACTOR_ID)).willReturn(round);
		givenGroupMember(payer);
		given(expenseRepository.save(any(Expense.class))).willAnswer(invocation -> {
			Expense saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", EXPENSE_ID);
			return saved;
		});

		ExpenseResponse response = expenseService.create(ROUND_ID, ACTOR_ID,
				new ExpenseRequest(100_000L, PAYER_ID, "  볼링  "));

		assertThat(response).isEqualTo(new ExpenseResponse(EXPENSE_ID, ROUND_ID, PAYER_ID, 100_000L, "볼링", List.of()));
		InOrder order = inOrder(settlementGuard, expenseRepository, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(expenseRepository).save(any(Expense.class));
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID);
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.EXPENSE, EXPENSE_ID,
				ChangeType.CREATE, null, new ExpenseSnapshot(ROUND_ID, PAYER_ID, 100_000L, "볼링", List.of()));
	}

	@Test
	@DisplayName("공백 메모는 null로 저장한다")
	void blankMemo() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, ACTOR_ID)).willReturn(round);
		givenGroupMember(payer);
		given(expenseRepository.save(any(Expense.class))).willAnswer(invocation -> invocation.getArgument(0));

		ExpenseResponse response = expenseService.create(ROUND_ID, ACTOR_ID, new ExpenseRequest(1L, PAYER_ID, "   "));

		assertThat(response.memo()).isNull();
	}

	@Test
	@DisplayName("비멤버가 등록하면 403이고 잠금·저장·로그가 없다")
	void createByNonMember() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, ACTOR_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertErrorCode(() -> expenseService.create(ROUND_ID, ACTOR_ID, new ExpenseRequest(1L, PAYER_ID, null)),
				ErrorCode.NOT_GROUP_MEMBER);

		verifyNoInteractions(settlementGuard, expenseRepository, changeLogRecorder);
	}

	@Test
	@DisplayName("결제자가 그룹 멤버가 아니면 INVALID_INPUT이고 저장하지 않는다")
	void createWithNonGroupPayer() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, ACTOR_ID)).willReturn(round);
		given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, PAYER_ID)).willReturn(Optional.empty());

		assertErrorCode(() -> expenseService.create(ROUND_ID, ACTOR_ID, new ExpenseRequest(1L, PAYER_ID, null)),
				ErrorCode.INVALID_INPUT);

		verify(expenseRepository, never()).save(any());
		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("수정하면 전/후 값으로 로그를 1건 남기고 분배 조정은 유지한다")
	void update() {
		Expense expense = givenExpense();
		expense.addShare(member(5L), null, null, true);
		Member newPayer = member(NEW_PAYER_ID);
		givenGroupMember(newPayer);
		List<ExpenseShareSnapshot> shares = List.of(new ExpenseShareSnapshot(5L, null, null, true));

		ExpenseResponse response = expenseService.update(EXPENSE_ID, ACTOR_ID,
				new ExpenseRequest(20_000L, NEW_PAYER_ID, "뒤풀이"));

		assertThat(response.amount()).isEqualTo(20_000L);
		assertThat(response.payerId()).isEqualTo(NEW_PAYER_ID);
		assertThat(response.shares()).hasSize(1);
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID);
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.EXPENSE, EXPENSE_ID,
				ChangeType.UPDATE,
				new ExpenseSnapshot(ROUND_ID, PAYER_ID, 10_000L, "밥", shares),
				new ExpenseSnapshot(ROUND_ID, NEW_PAYER_ID, 20_000L, "뒤풀이", shares));
	}

	@Test
	@DisplayName("없는 지출을 수정하면 EXPENSE_NOT_FOUND이고 잠그지 않는다")
	void updateNotFound() {
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID))
				.willThrow(new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));

		assertErrorCode(() -> expenseService.update(EXPENSE_ID, ACTOR_ID, new ExpenseRequest(1L, PAYER_ID, null)),
				ErrorCode.EXPENSE_NOT_FOUND);

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("삭제하면 조정까지 담은 삭제 전 값으로 로그를 1건 남긴다")
	void delete() {
		Expense expense = givenExpense();
		expense.addShare(member(5L), 3_000L, null, false);

		expenseService.delete(EXPENSE_ID, ACTOR_ID);

		InOrder order = inOrder(settlementGuard, expenseRepository, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(expenseRepository).delete(expense);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID);
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.EXPENSE, EXPENSE_ID,
				ChangeType.DELETE,
				new ExpenseSnapshot(ROUND_ID, PAYER_ID, 10_000L, "밥",
						List.of(new ExpenseShareSnapshot(5L, 3_000L, null, false))),
				null);
	}

	@Test
	@DisplayName("비멤버가 삭제하면 403이고 지우지 않는다")
	void deleteByNonMember() {
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertErrorCode(() -> expenseService.delete(EXPENSE_ID, ACTOR_ID), ErrorCode.NOT_GROUP_MEMBER);

		verifyNoInteractions(settlementGuard, expenseRepository, changeLogRecorder);
	}

	@Test
	@DisplayName("목록은 인가 후 저장소 순서대로 응답한다")
	void list() {
		Expense expense = Expense.create(round, payer, 1_000L, null);
		given(expenseRepository.findAllWithSharesByMeetingId(MEETING_ID)).willReturn(List.of(expense));

		assertThat(expenseService.list(MEETING_ID, ACTOR_ID)).singleElement()
				.satisfies(response -> assertThat(response.amount()).isEqualTo(1_000L));

		verify(meetingAccessChecker).requireMember(MEETING_ID, ACTOR_ID);
	}

	@Test
	@DisplayName("비멤버의 목록 조회는 403이고 지출을 읽지 않는다")
	void listByNonMember() {
		given(meetingAccessChecker.requireMember(MEETING_ID, ACTOR_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertErrorCode(() -> expenseService.list(MEETING_ID, ACTOR_ID), ErrorCode.NOT_GROUP_MEMBER);

		verifyNoInteractions(expenseRepository);
	}

	private Expense givenExpense() {
		Expense expense = Expense.create(round, payer, 10_000L, "밥");
		ReflectionTestUtils.setField(expense, "id", EXPENSE_ID);
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID)).willReturn(expense);
		return expense;
	}

	private void givenGroupMember(Member target) {
		given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, target.getId()))
				.willReturn(Optional.of(GroupMember.join(group, target, target.getNickname(), GroupRole.MEMBER)));
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id, "멤버" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode errorCode) {
		assertThatThrownBy(callable).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
	}
}
