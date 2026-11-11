package com.poco7250.moyeo.penalty.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
class PenaltyRuleRepositoryTest {

	private static final Long HOST_ID = 1L;
	private static final LocalDateTime SET_AT = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Autowired
	PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	GameSessionRepository gameSessionRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManager entityManager;

	private GameSession session;
	private GameSession otherSession;
	private Expense bowling;
	private Expense dinner;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		LocalDateTime startAt = LocalDateTime.of(2026, 12, 20, 18, 0);
		Meeting meeting = meetingRepository.saveAndFlush(
				Meeting.create(group, "연말 모임", startAt, null, null, HOST_ID));
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 1, "볼링"));
		session = saveGame(round);
		otherSession = saveGame(round);
		Member payer = memberRepository.saveAndFlush(Member.ofKakao(101L, "철수", null, null));
		bowling = expenseRepository.saveAndFlush(Expense.create(round, payer, 87_650L, "볼링"));
		dinner = expenseRepository.saveAndFlush(Expense.create(round, payer, 100_000L, "밥"));
	}

	@Test
	@DisplayName("게임당 금액 조건은 한 건만 저장된다")
	void gameSessionUnique() {
		penaltyRuleRepository.saveAndFlush(ratioRule(session, bowling));

		assertThatThrownBy(() -> penaltyRuleRepository.saveAndFlush(ratioRule(session, dinner)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("게임으로 금액 조건을 조회한다")
	void findsByGameSession() {
		penaltyRuleRepository.saveAndFlush(ratioRule(session, bowling));
		entityManager.clear();

		PenaltyRule found = penaltyRuleRepository.findByGameSessionId(session.getId()).orElseThrow();

		assertThat(found.getMoneyType()).isEqualTo(PenaltyMoneyType.RATIO);
		assertThat(found.getRatioPct()).isEqualTo(30);
		assertThat(found.getAmount()).isNull();
		assertThat(found.getTargetExpense().getId()).isEqualTo(bowling.getId());
		assertThat(found.getSetBy()).isEqualTo(HOST_ID);
		assertThat(found.getUpdatedAt()).isEqualTo(SET_AT);
		assertThat(penaltyRuleRepository.findByGameSessionId(otherSession.getId())).isEmpty();
	}

	@Test
	@DisplayName("같은 지출을 대상으로 한 금액 조건을 모두 조회한다")
	void findsAllByTargetExpense() {
		penaltyRuleRepository.saveAndFlush(ratioRule(session, bowling));
		penaltyRuleRepository.saveAndFlush(PenaltyRule.create(
				otherSession, PenaltyMoneyType.FIXED_AMOUNT, 10_000L, null, bowling, HOST_ID, SET_AT));
		entityManager.clear();

		assertThat(penaltyRuleRepository.findAllByTargetExpenseId(bowling.getId()))
				.extracting(rule -> rule.getGameSession().getId())
				.containsExactlyInAnyOrder(session.getId(), otherSession.getId());
		assertThat(penaltyRuleRepository.findAllByTargetExpenseId(dinner.getId())).isEmpty();
	}

	@Test
	@DisplayName("새 회차로 옮겨 저장해도 설정자와 설정 시각은 바뀌지 않는다")
	void moveToKeepsUpdatedAtInDatabase() {
		PenaltyRule rule = penaltyRuleRepository.saveAndFlush(ratioRule(session, bowling));

		rule.moveTo(otherSession);
		penaltyRuleRepository.saveAndFlush(rule);
		entityManager.clear();

		PenaltyRule moved = penaltyRuleRepository.findByGameSessionId(otherSession.getId()).orElseThrow();
		assertThat(moved.getId()).isEqualTo(rule.getId());
		assertThat(moved.getSetBy()).isEqualTo(HOST_ID);
		assertThat(moved.getUpdatedAt()).isEqualTo(SET_AT);
		assertThat(moved.getVersion()).isEqualTo(1L);
		assertThat(penaltyRuleRepository.findByGameSessionId(session.getId())).isEmpty();
	}

	private PenaltyRule ratioRule(GameSession gameSession, Expense targetExpense) {
		return PenaltyRule.create(gameSession, PenaltyMoneyType.RATIO, null, 30, targetExpense, HOST_ID, SET_AT);
	}

	private GameSession saveGame(Round round) {
		return gameSessionRepository.saveAndFlush(
				GameSession.create(round, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1));
	}
}
