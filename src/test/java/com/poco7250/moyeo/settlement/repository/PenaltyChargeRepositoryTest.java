package com.poco7250.moyeo.settlement.repository;

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
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
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
class PenaltyChargeRepositoryTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Autowired
	PenaltyChargeRepository penaltyChargeRepository;

	@Autowired
	SettlementRevisionRepository revisionRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	PenaltyRepository penaltyRepository;

	@Autowired
	PenaltyRuleRepository penaltyRuleRepository;

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

	private SettlementRevision revision;
	private Penalty penalty;
	private PenaltyRule rule;
	private Expense bowling;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(
				Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 2, "볼링장"));
		Member payer = memberRepository.saveAndFlush(Member.ofKakao(1001L, "철수", null, null));
		Member minsu = memberRepository.saveAndFlush(Member.ofKakao(1002L, "민수", null, null));
		GameSession game = gameSessionRepository.saveAndFlush(
				GameSession.create(round, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1));
		bowling = expenseRepository.saveAndFlush(Expense.create(round, payer, 100_000L, "볼링"));
		penalty = Penalty.assign(game, minsu);
		penalty.apply();
		penalty = penaltyRepository.saveAndFlush(penalty);
		rule = penaltyRuleRepository.saveAndFlush(
				PenaltyRule.create(game, PenaltyMoneyType.RATIO, null, 30, bowling, 1L, AT));
		Settlement settlement = settlementRepository.saveAndFlush(Settlement.createDraft(meeting));
		revision = revisionRepository.saveAndFlush(SettlementRevision.create(settlement, 1, payer, AT, false));
	}

	@Test
	@DisplayName("부담액과 확정 시점의 조건·대상 지출 값을 저장한다")
	void savesSnapshotColumns() {
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(revision, penalty, 30_000L, rule));
		entityManager.clear();

		assertThat(penaltyChargeRepository.findAllByRevisionIdOrderByIdAsc(revision.getId()))
				.singleElement()
				.satisfies(charge -> {
					assertThat(charge.getPenalty().getId()).isEqualTo(penalty.getId());
					assertThat(charge.getAmount()).isEqualTo(30_000L);
					assertThat(charge.getMoneyType()).isEqualTo(PenaltyMoneyType.RATIO);
					assertThat(charge.getRatioPct()).isEqualTo(30);
					assertThat(charge.getFixedAmount()).isNull();
					assertThat(charge.getTargetExpenseId()).isEqualTo(bowling.getId());
					assertThat(charge.getTargetExpenseAmount()).isEqualTo(100_000L);
					assertThat(charge.getTargetExpenseMemo()).isEqualTo("볼링");
				});
	}

	@Test
	@DisplayName("같은 리비전에 같은 벌칙 스냅샷을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateRevisionPenalty() {
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(revision, penalty, 30_000L, rule));

		assertThatThrownBy(() -> penaltyChargeRepository.saveAndFlush(
				PenaltyCharge.create(revision, penalty, 1_000L, rule)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("대상 지출과 조건이 삭제돼도 스냅샷은 그대로 남는다(대상 지출은 FK가 아니다)")
	void survivesTargetExpenseDelete() {
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(revision, penalty, 30_000L, rule));
		penaltyRuleRepository.delete(rule);
		penaltyRuleRepository.flush();
		expenseRepository.delete(bowling);
		expenseRepository.flush();
		entityManager.clear();

		assertThat(expenseRepository.findById(bowling.getId())).isEmpty();
		assertThat(penaltyChargeRepository.findAllByRevisionIdOrderByIdAsc(revision.getId()))
				.singleElement()
				.satisfies(charge -> {
					assertThat(charge.getTargetExpenseId()).isEqualTo(bowling.getId());
					assertThat(charge.getTargetExpenseMemo()).isEqualTo("볼링");
				});
	}
}
