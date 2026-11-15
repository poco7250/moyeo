package com.poco7250.moyeo.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.service.GameDrawer;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlaceRouletteTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 19, 18, 1);

	private Round round;
	private Member host;
	private PlaceCandidate first;
	private PlaceCandidate second;
	private PlaceCandidate third;

	@BeforeEach
	void setUp() {
		Meeting meeting = Meeting.create(Group.create("대학 동기"), "연말 모임",
				LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 7L);
		round = Round.create(meeting, 1, "밥");
		host = Member.ofKakao(1007L, "호스트", null, null);
		ReflectionTestUtils.setField(host, "id", 7L);
		first = candidate(500L, "고깃집");
		second = candidate(501L, "횟집");
		third = candidate(502L, "파스타");
	}

	private PlaceCandidate candidate(Long id, String label) {
		PlaceCandidate candidate = PlaceCandidate.propose(round, Place.of(label, null, null), host);
		ReflectionTestUtils.setField(candidate, "id", id);
		return candidate;
	}

	@Test
	@DisplayName("게임 룰렛과 같은 순서로 섞고 첫 번째 후보가 당첨된다")
	void winnerIsFirstOfGameDrawerOrder() {
		PlaceRoulette roulette = PlaceRoulette.spin(round, List.of(first, second, third), 42L, host, NOW);

		List<Long> expected = GameDrawer.order(List.of(500L, 501L, 502L), 42L);
		assertThat(roulette.getCandidateIds()).containsExactlyElementsOf(expected);
		assertThat(roulette.getWinner().getId()).isEqualTo(expected.get(0));
		assertThat(roulette.getSeed()).isEqualTo(42L);
		assertThat(roulette.getSpunBy()).isSameAs(host);
		assertThat(roulette.getSpunAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("같은 seed면 후보 입력 순서와 상관없이 같은 결과가 나온다")
	void reproducibleRegardlessOfInputOrder() {
		for (long seed = 0; seed < 20; seed++) {
			PlaceRoulette forward = PlaceRoulette.spin(round, List.of(first, second, third), seed, host, NOW);
			PlaceRoulette backward = PlaceRoulette.spin(round, List.of(third, second, first), seed, host, NOW);

			assertThat(backward.getCandidateIds()).isEqualTo(forward.getCandidateIds());
			assertThat(backward.getWinner()).isSameAs(forward.getWinner());
		}
	}

	@Test
	@DisplayName("대상 후보가 2개 미만이면 409 PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES")
	void notEnoughCandidates() {
		assertThatThrownBy(() -> PlaceRoulette.spin(round, List.of(first), 1L, host, NOW))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES);
		assertThatThrownBy(() -> PlaceRoulette.spin(round, List.of(), 1L, host, NOW))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES);
	}
}
