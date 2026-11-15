package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MeetingHistoryAssemblerTest {

	@Test
	@DisplayName("다시 돌린 회차가 공개되지 않았으면 원 회차가 남는다")
	void keepsOriginalWithoutRevealedRerun() {
		GameSession original = root(1L);

		assertThat(MeetingHistoryAssembler.latestRevealedPerRoot(List.of(original))).containsExactly(original);
	}

	@Test
	@DisplayName("같은 원 회차에서는 마지막으로 공개된 회차만 남고, 결과는 원 회차 순이다")
	void keepsLatestPerRoot() {
		GameSession first = root(1L);
		GameSession firstRerun = rerun(first, 3L);
		GameSession firstRerunAgain = rerun(firstRerun, 5L);
		GameSession second = root(2L);

		assertThat(MeetingHistoryAssembler.latestRevealedPerRoot(List.of(second, firstRerun, first, firstRerunAgain)))
				.containsExactly(firstRerunAgain, second);
	}

	private static GameSession root(Long id) {
		GameSession game = GameSession.create(null, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1);
		ReflectionTestUtils.setField(game, "id", id);
		game.markAsRoot();
		return game;
	}

	private static GameSession rerun(GameSession original, Long id) {
		GameSession game = GameSession.rerunOf(original, 1L);
		ReflectionTestUtils.setField(game, "id", id);
		return game;
	}
}
