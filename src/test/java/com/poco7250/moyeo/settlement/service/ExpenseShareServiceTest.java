package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareAdjustRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseShareItem;
import com.poco7250.moyeo.settlement.dto.ExpenseShareResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareSnapshot;
import com.poco7250.moyeo.settlement.dto.ExpenseSharesSnapshot;
import java.time.LocalDateTime;
import java.util.List;
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
class ExpenseShareServiceTest {

	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 2L;
	private static final Long EXPENSE_ID = 4L;
	private static final Long ACTOR_ID = 7L;

	@Mock
	ExpenseLoader expenseLoader;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@InjectMocks
	ExpenseShareService expenseShareService;

	private Group group;
	private Expense expense;

	@BeforeEach
	void setUp() {
		group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		Meeting meeting = Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, ACTOR_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		Round round = Round.create(meeting, 1, "볼링");
		expense = Expense.create(round, member(8L), 10_000L, null);
		ReflectionTestUtils.setField(expense, "id", EXPENSE_ID);
	}

	@Test
	@DisplayName("조정을 전체 교체하고 지출 단위 EXPENSE_SHARE 로그를 1건 남긴다")
	void adjust() {
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID)).willReturn(expense);
		Member kept = member(1L);
		expense.addShare(kept, null, null, true);
		expense.addShare(member(2L), null, 2, false);
		Member added = member(3L);
		givenGroupMembers(List.of(1L, 3L), kept, added);

		ExpenseResponse response = expenseShareService.adjust(EXPENSE_ID, ACTOR_ID, new ExpenseShareAdjustRequest(List.of(
				new ExpenseShareItem(1L, 5_000L, null, null),
				new ExpenseShareItem(3L, null, null, true))));

		assertThat(response.shares()).containsExactly(
				new ExpenseShareResponse(1L, 5_000L, null, false),
				new ExpenseShareResponse(3L, null, null, true));
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID);
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.EXPENSE_SHARE, EXPENSE_ID,
				ChangeType.UPDATE,
				new ExpenseSharesSnapshot(EXPENSE_ID, List.of(
						new ExpenseShareSnapshot(1L, null, null, true),
						new ExpenseShareSnapshot(2L, null, 2, false))),
				new ExpenseSharesSnapshot(EXPENSE_ID, List.of(
						new ExpenseShareSnapshot(1L, 5_000L, null, false),
						new ExpenseShareSnapshot(3L, null, null, true))));
	}

	@Test
	@DisplayName("빈 목록이면 멤버 조회 없이 조정을 모두 지우고 로그를 남긴다")
	void clearsAll() {
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID)).willReturn(expense);
		expense.addShare(member(1L), null, null, true);

		ExpenseResponse response = expenseShareService.adjust(EXPENSE_ID, ACTOR_ID,
				new ExpenseShareAdjustRequest(List.of()));

		assertThat(response.shares()).isEmpty();
		verifyNoInteractions(groupMemberRepository);
		verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.EXPENSE_SHARE, EXPENSE_ID,
				ChangeType.UPDATE,
				new ExpenseSharesSnapshot(EXPENSE_ID, List.of(new ExpenseShareSnapshot(1L, null, null, true))),
				new ExpenseSharesSnapshot(EXPENSE_ID, List.of()));
	}

	@Test
	@DisplayName("조정 대상에 그룹 멤버가 아닌 회원이 있으면 INVALID_INPUT이고 조정·로그가 없다")
	void nonGroupTarget() {
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID)).willReturn(expense);
		expense.addShare(member(1L), null, null, true);
		givenGroupMembers(List.of(1L, 3L), member(1L));

		assertErrorCode(() -> expenseShareService.adjust(EXPENSE_ID, ACTOR_ID, new ExpenseShareAdjustRequest(List.of(
				new ExpenseShareItem(1L, null, 2, null),
				new ExpenseShareItem(3L, null, null, true)))), ErrorCode.INVALID_INPUT);

		assertThat(expense.getShares()).singleElement().satisfies(share -> assertThat(share.isExcluded()).isTrue());
		verify(settlementGuard, never()).markAmountChanged(any(), any());
		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("비멤버가 조정하면 403이고 잠그지 않는다")
	void nonMember() {
		given(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertErrorCode(() -> expenseShareService.adjust(EXPENSE_ID, ACTOR_ID,
				new ExpenseShareAdjustRequest(List.of())), ErrorCode.NOT_GROUP_MEMBER);

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	private void givenGroupMembers(List<Long> requestedIds, Member... members) {
		given(groupMemberRepository.findAllByGroupIdAndMemberIdIn(GROUP_ID, requestedIds))
				.willReturn(List.of(members).stream()
						.map(member -> GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER))
						.toList());
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
