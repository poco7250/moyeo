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
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
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
class PenaltyRepositoryTest {

	private static final LocalDateTime WAIVED_AT = LocalDateTime.of(2026, 12, 20, 22, 0);

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

	private GameSession session;
	private GameSession otherSession;
	private Member first;
	private Member second;
	private Member third;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		LocalDateTime startAt = LocalDateTime.of(2026, 12, 20, 18, 0);
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(group, "연말 모임", startAt, null, null, 1L));
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 1, "볼링"));
		session = saveGame(round);
		otherSession = saveGame(round);
		first = memberRepository.saveAndFlush(Member.ofKakao(101L, "민수", null, null));
		second = memberRepository.saveAndFlush(Member.ofKakao(102L, "영희", null, null));
		third = memberRepository.saveAndFlush(Member.ofKakao(103L, "철수", null, null));
	}

	@Test
	@DisplayName("같은 게임에 같은 회원의 벌칙은 한 건만 저장된다")
	void gameSessionMemberUnique() {
		penaltyRepository.saveAndFlush(Penalty.assign(session, first));

		assertThatThrownBy(() -> penaltyRepository.saveAndFlush(Penalty.assign(session, first)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("다른 게임이면 같은 회원도 벌칙을 받을 수 있다")
	void sameMemberInOtherGame() {
		penaltyRepository.saveAndFlush(Penalty.assign(session, first));
		penaltyRepository.saveAndFlush(Penalty.assign(otherSession, first));

		assertThat(penaltyRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("저장한 벌칙은 ASSIGNED이고 면제 정보와 버전이 저장·조회된다")
	void savesAndReads() {
		Penalty penalty = penaltyRepository.saveAndFlush(Penalty.assign(session, first));
		penalty.waive(second.getId(), WAIVED_AT);
		penaltyRepository.saveAndFlush(penalty);
		entityManager.clear();

		Penalty found = penaltyRepository.findById(penalty.getId()).orElseThrow();

		assertThat(found.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(found.getWaivedBy()).isEqualTo(second.getId());
		assertThat(found.getWaivedAt()).isEqualTo(WAIVED_AT);
		assertThat(found.getVersion()).isEqualTo(1L);
		assertThat(found.getGameSession().getId()).isEqualTo(session.getId());
		assertThat(found.getMember().getId()).isEqualTo(first.getId());
	}

	@Test
	@DisplayName("게임의 비무효 벌칙만 조회한다(무효·다른 게임 제외)")
	void findsNonVoidedOfGame() {
		penaltyRepository.saveAndFlush(Penalty.assign(session, first));
		Penalty applied = Penalty.assign(session, second);
		applied.apply();
		penaltyRepository.saveAndFlush(applied);
		Penalty voided = Penalty.assign(session, third);
		voided.voidOut();
		penaltyRepository.saveAndFlush(voided);
		penaltyRepository.saveAndFlush(Penalty.assign(otherSession, third));
		entityManager.clear();

		assertThat(penaltyRepository.findAllByGameSessionIdAndStatusNot(session.getId(), PenaltyStatus.VOIDED))
				.extracting(penalty -> penalty.getMember().getId())
				.containsExactlyInAnyOrder(first.getId(), second.getId());
	}

	private GameSession saveGame(Round round) {
		return gameSessionRepository.saveAndFlush(
				GameSession.create(round, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1));
	}
}
