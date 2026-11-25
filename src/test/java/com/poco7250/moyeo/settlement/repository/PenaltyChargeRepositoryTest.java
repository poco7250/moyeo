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
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
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

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(
				Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 2, "볼링장"));
		Member payer = memberRepository.saveAndFlush(Member.ofKakao(1001L, "철수", null, null));
		Member minsu = memberRepository.saveAndFlush(Member.ofKakao(1002L, "민수", null, null));
		GameSession game = gameSessionRepository.saveAndFlush(
				GameSession.create(round.getMeeting(), GameType.LADDER, 1L, PenaltyKind.MONEY, 10_000L, null, 1));
		penalty = penaltyRepository.saveAndFlush(Penalty.assign(game, minsu));
		Settlement settlement = settlementRepository.saveAndFlush(Settlement.createDraft(meeting));
		revision = revisionRepository.saveAndFlush(SettlementRevision.create(settlement, 1, payer, AT, false));
	}

	@Test
	@DisplayName("부담액과 확정 시점의 게임 금액을 고정 금액으로 저장하고, 대상 지출 값은 비운다")
	void savesSnapshotColumns() {
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(revision, penalty, 10_000L, 10_000L));
		entityManager.clear();

		assertThat(penaltyChargeRepository.findAllByRevisionIdOrderByIdAsc(revision.getId()))
				.singleElement()
				.satisfies(charge -> {
					assertThat(charge.getPenalty().getId()).isEqualTo(penalty.getId());
					assertThat(charge.getAmount()).isEqualTo(10_000L);
					assertThat(charge.getMoneyType()).isEqualTo(PenaltyMoneyType.FIXED_AMOUNT);
					assertThat(charge.getFixedAmount()).isEqualTo(10_000L);
					assertThat(charge.getRatioPct()).isNull();
					assertThat(charge.getTargetExpenseId()).isNull();
					assertThat(charge.getTargetExpenseAmount()).isNull();
					assertThat(charge.getTargetExpenseMemo()).isNull();
				});
	}

	@Test
	@DisplayName("같은 리비전에 같은 벌칙 스냅샷을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateRevisionPenalty() {
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(revision, penalty, 30_000L, 10_000L));

		assertThatThrownBy(() -> penaltyChargeRepository.saveAndFlush(
				PenaltyCharge.create(revision, penalty, 1_000L, 10_000L)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("여러 리비전의 스냅샷을 벌칙과 함께 한 번에 조회하고, 목록에 없는 리비전은 뺀다")
	void findWithPenaltyByRevisionIds() {
		SettlementRevision second = revisionRepository.saveAndFlush(
				SettlementRevision.create(revision.getSettlement(), 2, revision.getConfirmedBy(), AT, false));
		SettlementRevision excluded = revisionRepository.saveAndFlush(
				SettlementRevision.create(revision.getSettlement(), 3, revision.getConfirmedBy(), AT, false));
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(revision, penalty, 30_000L, 10_000L));
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(second, penalty, 50_000L, 10_000L));
		penaltyChargeRepository.saveAndFlush(PenaltyCharge.create(excluded, penalty, 1L, 10_000L));
		entityManager.clear();

		List<PenaltyCharge> charges = penaltyChargeRepository.findAllWithPenaltyByRevisionIdIn(
				List.of(revision.getId(), second.getId()));

		assertThat(charges).extracting(PenaltyCharge::getAmount).containsExactly(30_000L, 50_000L);
		assertThat(charges).allSatisfy(charge -> {
			assertThat(Hibernate.isInitialized(charge.getPenalty())).isTrue();
			assertThat(charge.getPenalty().getMember().getId()).isEqualTo(penalty.getMember().getId());
		});
	}
}
