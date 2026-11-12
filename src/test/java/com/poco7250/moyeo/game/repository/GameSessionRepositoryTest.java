package com.poco7250.moyeo.game.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class GameSessionRepositoryTest {

	@Autowired
	GameSessionRepository gameSessionRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	EntityManager entityManager;

	private Group group;
	private Round round;

	@BeforeEach
	void setUp() {
		group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		LocalDateTime startAt = LocalDateTime.of(2026, 12, 20, 18, 0);
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(group, "연말 모임", startAt, null, null, 1L));
		round = roundRepository.saveAndFlush(Round.create(meeting, 1, "볼링"));
	}

	@Test
	@DisplayName("한 회차를 가리키는 다시 돌린 회차는 하나뿐이다")
	void rerunOfUnique() {
		GameSession original = save(newGame());
		save(rerunOf(original));

		assertThatThrownBy(() -> save(rerunOf(original)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("rerunOf가 없는 게임은 여러 건 저장할 수 있다")
	void rerunOfAllowsMultipleNulls() {
		save(newGame());

		assertThatCode(() -> save(newGame())).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("applyOrderAt을 마이크로초까지 저장하고 조회한다")
	void applyOrderAtKeepsMicros() {
		LocalDateTime revealedAt = LocalDateTime.of(2026, 12, 20, 21, 0, 0, 123_456_000);
		GameSession session = newGame();
		session.start();
		session.reveal(revealedAt);
		Long id = save(session).getId();
		entityManager.clear();

		assertThat(gameSessionRepository.findById(id).orElseThrow().getApplyOrderAt()).isEqualTo(revealedAt);
	}

	@Test
	@DisplayName("applyOrderAt 컬럼 정밀도는 6이다")
	void applyOrderAtColumnPrecision() {
		Number precision = (Number) entityManager.createNativeQuery("""
				select datetime_precision from information_schema.columns
				where table_schema = database() and table_name = 'game_session'
				and column_name = 'apply_order_at'
				""").getSingleResult();

		assertThat(precision.intValue()).isEqualTo(6);
	}

	@Test
	@DisplayName("게임이 속한 그룹 id를 조회한다")
	void findGroupIdById() {
		Long id = save(newGame()).getId();

		assertThat(gameSessionRepository.findGroupIdById(id)).contains(group.getId());
	}

	@Test
	@DisplayName("없는 게임의 그룹 id는 비어 있다")
	void findGroupIdByIdMissing() {
		assertThat(gameSessionRepository.findGroupIdById(Long.MAX_VALUE)).isEmpty();
	}

	@Test
	@DisplayName("차수의 게임을 최근에 만든 순으로 조회하고 다른 차수 게임은 제외한다")
	void findAllByRoundIdOrderByIdDesc() {
		GameSession first = save(newGame());
		GameSession second = save(newGame());
		Round otherRound = roundRepository.saveAndFlush(Round.create(round.getMeeting(), 2, "노래방"));
		save(GameSession.create(otherRound, GameType.DRAW, 1L, PenaltyKind.NONE, null, 1));

		assertThat(gameSessionRepository.findAllByRoundIdOrderByIdDesc(round.getId()))
				.extracting(GameSession::getId)
				.containsExactly(second.getId(), first.getId());
	}

	@Test
	@DisplayName("후속 회차 유무는 rerunOf로 확인하고, 취소로 rerunOf를 비운 회차는 세지 않는다")
	void existsByRerunOfId() {
		GameSession original = save(newGame());
		assertThat(gameSessionRepository.existsByRerunOfId(original.getId())).isFalse();

		GameSession rerun = save(GameSession.rerunOf(original, 2L));
		assertThat(gameSessionRepository.existsByRerunOfId(original.getId())).isTrue();

		rerun.cancel();
		save(rerun);
		entityManager.clear();
		assertThat(gameSessionRepository.existsByRerunOfId(original.getId())).isFalse();
		assertThat(gameSessionRepository.findById(rerun.getId()).orElseThrow().getCanceledRerunOf().getId())
				.isEqualTo(original.getId());
	}

	private GameSession newGame() {
		return GameSession.create(round, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1);
	}

	private GameSession rerunOf(GameSession original) {
		GameSession rerun = newGame();
		ReflectionTestUtils.setField(rerun, "rerunOf", original);
		return rerun;
	}

	private GameSession save(GameSession session) {
		return gameSessionRepository.saveAndFlush(session);
	}
}
