package com.poco7250.moyeo.place.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteTallyTest {

	@Test
	@DisplayName("최다 득표 후보가 하나면 그 후보가 winner다")
	void singleWinner() {
		VoteTally.Decision decision = VoteTally.decide(List.of(1L, 2L, 3L), Map.of(1L, 1L, 2L, 3L, 3L, 2L));

		assertThat(decision.hasWinner()).isTrue();
		assertThat(decision.winnerId()).isEqualTo(2L);
		assertThat(decision.tiedCandidateIds()).isEmpty();
	}

	@Test
	@DisplayName("표가 없는 후보가 맵에 없어도 단독 최다를 고른다")
	void winnerWithMissingCounts() {
		VoteTally.Decision decision = VoteTally.decide(List.of(1L, 2L), Map.of(2L, 1L));

		assertThat(decision.winnerId()).isEqualTo(2L);
	}

	@Test
	@DisplayName("2개 동점이면 winner 없이 동점 후보만 돌려준다")
	void twoWayTie() {
		VoteTally.Decision decision = VoteTally.decide(List.of(1L, 2L, 3L), Map.of(1L, 2L, 2L, 2L, 3L, 1L));

		assertThat(decision.hasWinner()).isFalse();
		assertThat(decision.tiedCandidateIds()).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("3개 동점이면 세 후보 모두 돌려준다")
	void threeWayTie() {
		VoteTally.Decision decision = VoteTally.decide(List.of(1L, 2L, 3L), Map.of(1L, 1L, 2L, 1L, 3L, 1L));

		assertThat(decision.hasWinner()).isFalse();
		assertThat(decision.tiedCandidateIds()).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("전원 0표면 전체 후보가 호스트 선택 대상이다")
	void allZeroVotes() {
		VoteTally.Decision decision = VoteTally.decide(List.of(1L, 2L), Map.of());

		assertThat(decision.hasWinner()).isFalse();
		assertThat(decision.tiedCandidateIds()).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("후보가 1개여도 0표면 자동 확정하지 않는다")
	void singleCandidateWithoutVotes() {
		VoteTally.Decision decision = VoteTally.decide(List.of(1L), Map.of());

		assertThat(decision.hasWinner()).isFalse();
		assertThat(decision.tiedCandidateIds()).containsExactly(1L);
	}

	@Test
	@DisplayName("후보가 없으면 winner도 고를 후보도 없다")
	void noCandidates() {
		VoteTally.Decision decision = VoteTally.decide(List.of(), Map.of());

		assertThat(decision.hasWinner()).isFalse();
		assertThat(decision.tiedCandidateIds()).isEmpty();
	}
}
