package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HallOfFameAssemblerTest {

	@Test
	@DisplayName("정렬된 값이 앞 사람과 같으면 같은 순위, 다르면 자기 자리 순위(1, 1, 3, 3, 5)")
	void competitionRanks() {
		List<Long> sorted = List.of(4L, 4L, 2L, 2L, 0L);

		assertThat(HallOfFameAssembler.competitionRanks(sorted, Long::longValue)).containsExactly(1, 1, 3, 3, 5);
	}

	@Test
	@DisplayName("모두 같으면 전원 1위이고 빈 목록은 빈 순위다")
	void allTiedOrEmpty() {
		assertThat(HallOfFameAssembler.competitionRanks(List.of(0L, 0L, 0L), Long::longValue))
				.containsExactly(1, 1, 1);
		assertThat(HallOfFameAssembler.competitionRanks(List.<Long>of(), Long::longValue)).isEmpty();
	}
}
