package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpenseLoaderTest {

	private static final Long MEETING_ID = 2L;
	private static final Long EXPENSE_ID = 4L;
	private static final Long ACTOR_ID = 7L;

	@Mock
	ExpenseRepository expenseRepository;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@InjectMocks
	ExpenseLoader expenseLoader;

	@Test
	@DisplayName("그룹 멤버면 지출을 돌려준다")
	void loads() {
		Expense expense = givenExpense();

		assertThat(expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID)).isSameAs(expense);
	}

	@Test
	@DisplayName("없는 지출은 EXPENSE_NOT_FOUND이고 인가 검사까지 가지 않는다")
	void notFound() {
		given(expenseRepository.findWithRoundById(EXPENSE_ID)).willReturn(Optional.empty());

		assertErrorCode(ErrorCode.EXPENSE_NOT_FOUND);

		verifyNoInteractions(meetingAccessChecker);
	}

	@Test
	@DisplayName("지출이 속한 모임의 그룹 멤버가 아니면 NOT_GROUP_MEMBER")
	void nonMember() {
		givenExpense();
		given(meetingAccessChecker.requireMember(MEETING_ID, ACTOR_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertErrorCode(ErrorCode.NOT_GROUP_MEMBER);
	}

	private Expense givenExpense() {
		Meeting meeting = Meeting.create(Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null,
				null, ACTOR_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		Expense expense = Expense.create(Round.create(meeting, 1, "밥"), Member.ofKakao(1L, "철수", null, null), 1_000L,
				null);
		given(expenseRepository.findWithRoundById(EXPENSE_ID)).willReturn(Optional.of(expense));
		return expense;
	}

	private void assertErrorCode(ErrorCode errorCode) {
		assertThatThrownBy(() -> expenseLoader.loadForMember(EXPENSE_ID, ACTOR_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
	}
}
