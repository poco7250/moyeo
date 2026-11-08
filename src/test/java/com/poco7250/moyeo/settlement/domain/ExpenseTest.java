package com.poco7250.moyeo.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpenseTest {

	private final Round round = Round.create(
			Meeting.create(Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L),
			1,
			"밥");
	private final Member payer = Member.ofKakao(1L, "철수", null, null);

	@ParameterizedTest
	@ValueSource(longs = {0L, -1L, 100_000_001L})
	@DisplayName("지출 금액이 1원~1억 원을 벗어나면 INVALID_INPUT이다")
	void rejectsOutOfRangeAmount(long amount) {
		assertInvalidInput(() -> Expense.create(round, payer, amount, null));
	}

	@ParameterizedTest
	@ValueSource(longs = {1L, 100_000_000L})
	@DisplayName("지출 금액 경계값 1원과 1억 원은 허용한다")
	void acceptsBoundaryAmount(long amount) {
		assertThat(Expense.create(round, payer, amount, "볼링").getAmount()).isEqualTo(amount);
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 100_000_001L})
	@DisplayName("고정 금액이 범위를 벗어나면 조정을 추가하지 않는다")
	void rejectsOutOfRangeFixedAmount(long fixedAmount) {
		Expense expense = Expense.create(round, payer, 10_000L, null);

		assertInvalidInput(() -> expense.addShare(payer, fixedAmount, null, false));
		assertThat(expense.getShares()).isEmpty();
	}

	@Test
	@DisplayName("고정 금액 없이 제외·가중치 조정을 추가할 수 있다")
	void addsShareWithoutFixedAmount() {
		Expense expense = Expense.create(round, payer, 10_000L, null);

		ExpenseShare share = expense.addShare(payer, null, 2, true);

		assertThat(expense.getShares()).containsExactly(share);
		assertThat(share.getExpense()).isSameAs(expense);
		assertThat(share.getAmount()).isNull();
		assertThat(share.getWeight()).isEqualTo(2);
		assertThat(share.isExcluded()).isTrue();
	}

	private void assertInvalidInput(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
	}
}
