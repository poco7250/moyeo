package com.poco7250.moyeo.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.ExpenseReceipt;
import com.poco7250.moyeo.settlement.domain.ReceiptImageType;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ExpenseReceiptRepositoryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

	@Autowired
	ExpenseReceiptRepository expenseReceiptRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MemberRepository memberRepository;

	private Expense first;
	private Expense second;
	private Expense third;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
		Member payer = memberRepository.saveAndFlush(Member.ofKakao(1L, "철수", null, null));
		first = expenseRepository.saveAndFlush(Expense.create(round, payer, 10_000L, null));
		second = expenseRepository.saveAndFlush(Expense.create(round, payer, 20_000L, null));
		third = expenseRepository.saveAndFlush(Expense.create(round, payer, 30_000L, null));
	}

	@Test
	@DisplayName("지출 1건에 영수증 행을 두 번 만들면 expense_id 유니크 위반이다")
	void oneReceiptPerExpense() {
		expenseReceiptRepository.saveAndFlush(receipt(first, "a.jpg"));

		assertThatThrownBy(() -> expenseReceiptRepository.saveAndFlush(receipt(first, "b.jpg")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("지출 id로 단건·여러 건을 조회한다")
	void findByExpense() {
		expenseReceiptRepository.saveAndFlush(receipt(first, "a.jpg"));
		expenseReceiptRepository.saveAndFlush(receipt(third, "c.jpg"));

		assertThat(expenseReceiptRepository.findByExpenseId(first.getId())).get()
				.extracting(ExpenseReceipt::getStorageKey).isEqualTo("a.jpg");
		assertThat(expenseReceiptRepository.findByExpenseId(second.getId())).isEmpty();
		assertThat(expenseReceiptRepository.findAllByExpenseIdIn(
				List.of(first.getId(), second.getId(), third.getId())))
				.extracting(ExpenseReceipt::getStorageKey).containsExactlyInAnyOrder("a.jpg", "c.jpg");
	}

	private static ExpenseReceipt receipt(Expense expense, String key) {
		return ExpenseReceipt.create(expense, key, ReceiptImageType.JPEG, 100L, 1L, NOW);
	}
}
