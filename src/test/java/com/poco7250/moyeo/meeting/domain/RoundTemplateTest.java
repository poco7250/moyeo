package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoundTemplateTest {

	@Test
	void 템플릿마다_차수_이름을_1차부터_순서대로_준다() {
		assertThat(RoundTemplate.MEAL_ONLY.roundNames()).containsExactly("밥");
		assertThat(RoundTemplate.MEAL_BOWLING.roundNames()).containsExactly("밥", "볼링");
		assertThat(RoundTemplate.MEAL_BOARD_GAME.roundNames()).containsExactly("밥", "보드게임");
		assertThat(RoundTemplate.MEAL_BOWLING_BOARD_GAME.roundNames()).containsExactly("밥", "볼링", "보드게임");
	}
}
