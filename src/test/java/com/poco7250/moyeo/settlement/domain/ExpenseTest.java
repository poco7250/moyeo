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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

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

	@Test
	@DisplayName("지출을 수정하면 결제자·금액·메모가 바뀌고 분배 조정은 유지된다")
	void updatesExpense() {
		Expense expense = Expense.create(round, payer, 10_000L, "밥");
		ExpenseShare share = expense.addShare(member(10L), null, null, true);
		Member newPayer = member(11L);

		expense.update(newPayer, 20_000L, "볼링");

		assertThat(expense.getPayer()).isSameAs(newPayer);
		assertThat(expense.getAmount()).isEqualTo(20_000L);
		assertThat(expense.getMemo()).isEqualTo("볼링");
		assertThat(expense.getShares()).containsExactly(share);
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, -1L, 100_000_001L})
	@DisplayName("범위를 벗어난 금액으로 수정하면 INVALID_INPUT이고 값은 그대로다")
	void rejectsOutOfRangeUpdate(long amount) {
		Expense expense = Expense.create(round, payer, 10_000L, "밥");

		assertInvalidInput(() -> expense.update(member(11L), amount, "볼링"));

		assertThat(expense.getPayer()).isSameAs(payer);
		assertThat(expense.getAmount()).isEqualTo(10_000L);
		assertThat(expense.getMemo()).isEqualTo("밥");
	}

	@ParameterizedTest
	@ValueSource(longs = {1L, 100_000_000L})
	@DisplayName("금액 경계값 1원과 1억 원으로 수정할 수 있다")
	void updatesBoundaryAmount(long amount) {
		Expense expense = Expense.create(round, payer, 10_000L, null);

		expense.update(payer, amount, null);

		assertThat(expense.getAmount()).isEqualTo(amount);
	}

	@Test
	@DisplayName("putShare는 같은 회원의 행을 새로 만들지 않고 고친다")
	void putShareUpdatesExisting() {
		Expense expense = Expense.create(round, payer, 10_000L, null);
		Member target = member(10L);
		ExpenseShare share = expense.addShare(target, null, null, true);

		expense.putShare(target, null, 3, false);

		assertThat(expense.getShares()).containsExactly(share);
		assertThat(share.getWeight()).isEqualTo(3);
		assertThat(share.isExcluded()).isFalse();
	}

	@Test
	@DisplayName("putShare는 조정이 없던 회원이면 새로 추가한다")
	void putShareAddsNew() {
		Expense expense = Expense.create(round, payer, 10_000L, null);
		expense.addShare(member(10L), null, null, true);

		expense.putShare(member(11L), 5_000L, null, false);

		assertThat(expense.getShares()).hasSize(2);
		assertThat(expense.getShares().get(1).getAmount()).isEqualTo(5_000L);
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 100_000_001L})
	@DisplayName("putShare로 범위를 벗어난 고정 금액을 넣으면 기존 조정은 그대로다")
	void putShareRejectsOutOfRangeFixedAmount(long fixedAmount) {
		Expense expense = Expense.create(round, payer, 10_000L, null);
		Member target = member(10L);
		ExpenseShare share = expense.addShare(target, 1_000L, null, false);

		assertInvalidInput(() -> expense.putShare(target, fixedAmount, null, false));

		assertThat(share.getAmount()).isEqualTo(1_000L);
	}

	@Test
	@DisplayName("removeSharesExcept는 주어진 회원의 조정만 남긴다")
	void removeSharesExcept() {
		Expense expense = Expense.create(round, payer, 10_000L, null);
		ExpenseShare kept = expense.addShare(member(10L), null, 2, false);
		expense.addShare(member(11L), null, null, true);

		expense.removeSharesExcept(Set.of(10L));

		assertThat(expense.getShares()).containsExactly(kept);
	}

	private Member member(long id) {
		Member member = Member.ofKakao(id, "멤버" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private void assertInvalidInput(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
	}
}
