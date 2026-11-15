package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameDrawerTest {

	private static final List<Long> MEMBER_IDS = List.of(1L, 2L, 3L, 4L, 5L);

	@ParameterizedTest
	@ValueSource(ints = {1, 2, 4})
	@DisplayName("요청한 수만큼 참가자 중에서 벌칙자를 뽑는다")
	void drawsRequestedCount(int penaltyCount) {
		Set<Long> penalized = GameDrawer.draw(MEMBER_IDS, penaltyCount, 42L);

		assertThat(penalized).hasSize(penaltyCount);
		assertThat(MEMBER_IDS).containsAll(penalized);
	}

	@Test
	@DisplayName("같은 seed면 같은 결과가 나온다")
	void reproducibleWithSeed() {
		assertThat(GameDrawer.draw(MEMBER_IDS, 2, 7L)).isEqualTo(GameDrawer.draw(MEMBER_IDS, 2, 7L));
	}

	@Test
	@DisplayName("참가자 입력 순서는 결과에 영향이 없다")
	void independentOfInputOrder() {
		assertThat(GameDrawer.draw(List.of(5L, 3L, 1L, 4L, 2L), 2, 7L)).isEqualTo(GameDrawer.draw(MEMBER_IDS, 2, 7L));
	}

	@Test
	@DisplayName("순서 정하기는 참가자 전원을 한 번씩 담고, 같은 seed면 입력 순서와 상관없이 같은 순서다")
	void orderReproducible() {
		List<Long> order = GameDrawer.order(MEMBER_IDS, 7L);

		assertThat(order).containsExactlyInAnyOrderElementsOf(MEMBER_IDS);
		assertThat(GameDrawer.order(List.of(5L, 3L, 1L, 4L, 2L), 7L)).isEqualTo(order);
	}

	@Test
	@DisplayName("벌칙자는 같은 seed로 정한 순서의 앞에서부터 뽑힌다")
	void drawTakesFrontOfOrder() {
		assertThat(GameDrawer.draw(MEMBER_IDS, 2, 7L))
				.isEqualTo(Set.copyOf(GameDrawer.order(MEMBER_IDS, 7L).subList(0, 2)));
	}

	@Test
	@DisplayName("seed가 달라지면 다른 사람도 뽑힌다")
	void seedChangesResult() {
		long distinctResults = java.util.stream.LongStream.range(0, 50)
				.mapToObj(seed -> GameDrawer.draw(MEMBER_IDS, 1, seed))
				.distinct()
				.count();

		assertThat(distinctResults).isGreaterThan(1);
	}
}
