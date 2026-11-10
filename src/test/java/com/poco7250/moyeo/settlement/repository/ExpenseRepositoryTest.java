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
import com.poco7250.moyeo.settlement.domain.ExpenseShare;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;
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
class ExpenseRepositoryTest {

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	ExpenseShareRepository expenseShareRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManager entityManager;

	private Round round;
	private Member payer;
	private Member other;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
		round = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
		payer = memberRepository.saveAndFlush(Member.ofKakao(1L, "철수", null, null));
		other = memberRepository.saveAndFlush(Member.ofKakao(2L, "지수", null, null));
	}

	@Test
	@DisplayName("지출이 있는 차수의 id만 중복 없이 돌려준다")
	void findsRoundIdsWithExpense() {
		Round withoutExpense = roundRepository.saveAndFlush(Round.create(round.getMeeting(), 2, "볼링"));
		expenseRepository.saveAndFlush(Expense.create(round, payer, 1_000L, null));
		expenseRepository.saveAndFlush(Expense.create(round, other, 2_000L, null));

		assertThat(expenseRepository.findRoundIdsWithExpense(List.of(round.getId(), withoutExpense.getId())))
				.containsExactly(round.getId());
	}

	@Test
	@DisplayName("지출과 조정을 함께 저장하고 다시 읽는다")
	void savesExpenseWithShares() {
		Expense expense = Expense.create(round, payer, 100_000L, "볼링");
		expense.addShare(other, 5_000L, 2, false);
		Long expenseId = expenseRepository.saveAndFlush(expense).getId();
		entityManager.clear();

		Expense found = expenseRepository.findById(expenseId).orElseThrow();

		assertThat(found.getAmount()).isEqualTo(100_000L);
		assertThat(found.getMemo()).isEqualTo("볼링");
		assertThat(found.getRound().getId()).isEqualTo(round.getId());
		assertThat(found.getPayer().getId()).isEqualTo(payer.getId());
		assertThat(found.getShares()).singleElement().satisfies(share -> {
			assertThat(share.getMember().getId()).isEqualTo(other.getId());
			assertThat(share.getAmount()).isEqualTo(5_000L);
			assertThat(share.getWeight()).isEqualTo(2);
			assertThat(share.isExcluded()).isFalse();
		});
	}

	@Test
	@DisplayName("지출을 삭제하면 조정도 함께 삭제된다")
	void deletesSharesWithExpense() {
		Expense expense = Expense.create(round, payer, 100_000L, null);
		expense.addShare(other, null, null, true);
		expenseRepository.saveAndFlush(expense);
		assertThat(expenseShareRepository.count()).isEqualTo(1);

		expenseRepository.delete(expense);
		expenseRepository.flush();

		assertThat(expenseRepository.count()).isZero();
		assertThat(expenseShareRepository.count()).isZero();
	}

	@Test
	@DisplayName("지출 목록에서 뺀 조정은 고아로 삭제된다")
	void removesOrphanShare() {
		Expense expense = Expense.create(round, payer, 100_000L, null);
		ExpenseShare share = expense.addShare(other, null, null, true);
		expenseRepository.saveAndFlush(expense);

		expense.getShares().remove(share);
		expenseRepository.flush();

		assertThat(expenseShareRepository.count()).isZero();
	}

	@Test
	@DisplayName("차수와 함께 지출을 조회하고, 없는 지출은 비어 있다")
	void findsWithRound() {
		Long expenseId = expenseRepository.saveAndFlush(Expense.create(round, payer, 100_000L, null)).getId();
		entityManager.clear();

		assertThat(expenseRepository.findWithRoundById(expenseId)).hasValueSatisfying(
				found -> assertThat(Hibernate.isInitialized(found.getRound())).isTrue());
		assertThat(expenseRepository.findWithRoundById(expenseId + 1)).isEmpty();
	}

	@Test
	@DisplayName("모임 지출 목록은 차수 순·등록 순이고 조정 없는 지출도 포함하며 다른 모임은 제외한다")
	void listsByMeeting() {
		Round secondRound = roundRepository.saveAndFlush(Round.create(round.getMeeting(), 2, "볼링"));
		Round otherRound = roundRepository.saveAndFlush(Round.create(saveOtherMeeting(), 1, "밥"));
		Expense inSecond = expenseRepository.saveAndFlush(Expense.create(secondRound, payer, 3_000L, null));
		Expense first = Expense.create(round, payer, 1_000L, null);
		first.addShare(payer, null, 2, false);
		first.addShare(other, null, null, true);
		expenseRepository.saveAndFlush(first);
		Expense second = expenseRepository.saveAndFlush(Expense.create(round, payer, 2_000L, null));
		expenseRepository.saveAndFlush(Expense.create(otherRound, payer, 9_000L, null));
		entityManager.clear();

		var found = expenseRepository.findAllWithSharesByMeetingId(round.getMeeting().getId());

		assertThat(found).extracting(Expense::getId)
				.containsExactly(first.getId(), second.getId(), inSecond.getId());
		assertThat(found.getFirst().getShares()).hasSize(2);
		assertThat(found.get(1).getShares()).isEmpty();
	}

	private Meeting saveOtherMeeting() {
		Group group = groupRepository.saveAndFlush(Group.create("고등 동창"));
		return meetingRepository.saveAndFlush(Meeting.create(
				group, "신년 모임", LocalDateTime.of(2027, 1, 2, 18, 0), null, null, 1L));
	}

	@Test
	@DisplayName("한 지출에 같은 회원의 조정을 두 건 저장하면 유니크 제약 위반이다")
	void duplicateShare() {
		Expense expense = Expense.create(round, payer, 100_000L, null);
		expense.addShare(other, null, null, true);
		expense.addShare(other, null, 2, false);

		assertThatThrownBy(() -> expenseRepository.saveAndFlush(expense))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
