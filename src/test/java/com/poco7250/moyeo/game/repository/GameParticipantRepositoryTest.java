package com.poco7250.moyeo.game.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
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
class GameParticipantRepositoryTest {

	@Autowired
	GameParticipantRepository gameParticipantRepository;

	@Autowired
	GameResultRepository gameResultRepository;

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
	}

	@Test
	@DisplayName("참가자는 준비 전 상태로 저장된다")
	void savedNotReady() {
		GameParticipant saved = gameParticipantRepository.saveAndFlush(GameParticipant.create(session, first));

		assertThat(saved.isReady()).isFalse();
		assertThat(saved.getPickedSlot()).isNull();
	}

	@Test
	@DisplayName("같은 게임에 같은 회원은 한 번만 참가한다")
	void memberUnique() {
		gameParticipantRepository.saveAndFlush(GameParticipant.create(session, first));

		assertThatThrownBy(() -> gameParticipantRepository.saveAndFlush(GameParticipant.create(session, first)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 게임에서 같은 카드는 한 명만 고를 수 있다")
	void pickedSlotUnique() {
		gameParticipantRepository.saveAndFlush(participantWithSlot(session, first, 1));

		assertThatThrownBy(() -> gameParticipantRepository.saveAndFlush(participantWithSlot(session, second, 1)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("다른 게임이면 같은 카드 번호를 쓸 수 있다")
	void pickedSlotAllowedInOtherGame() {
		gameParticipantRepository.saveAndFlush(participantWithSlot(session, first, 1));

		assertThatCode(() -> gameParticipantRepository.saveAndFlush(participantWithSlot(otherSession, first, 1)))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("아직 카드를 고르지 않은 참가자는 여러 명일 수 있다")
	void pickedSlotAllowsMultipleNulls() {
		gameParticipantRepository.saveAndFlush(GameParticipant.create(session, first));

		assertThatCode(() -> gameParticipantRepository.saveAndFlush(GameParticipant.create(session, second)))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("게임 결과를 저장하고 조회한다")
	void savesGameResult() {
		Long id = gameResultRepository.saveAndFlush(GameResult.create(session, first, "꽝")).getId();
		entityManager.clear();

		GameResult found = gameResultRepository.findById(id).orElseThrow();

		assertThat(found.getOutcome()).isEqualTo("꽝");
		assertThat(found.getMember().getId()).isEqualTo(first.getId());
		assertThat(found.getGameSession().getId()).isEqualTo(session.getId());
	}

	private GameParticipant participantWithSlot(GameSession gameSession, Member member, int slot) {
		GameParticipant participant = GameParticipant.create(gameSession, member);
		participant.pick(slot);
		return participant;
	}

	private GameSession saveGame(Round round) {
		return gameSessionRepository.saveAndFlush(
				GameSession.create(round, GameType.DRAW, 1L, PenaltyKind.NONE, null, null));
	}
}
