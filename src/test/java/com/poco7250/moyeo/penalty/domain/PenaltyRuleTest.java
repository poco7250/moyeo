package com.poco7250.moyeo.penalty.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PenaltyRuleTest {

	private static final Long HOST_ID = 2L;
	private static final LocalDateTime SET_AT = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Test
	@DisplayName("고정 금액 조건은 금액만 갖는다")
	void createFixedAmount() {
		PenaltyRule rule = create(PenaltyMoneyType.FIXED_AMOUNT, 10_000L, null);

		assertThat(rule.getMoneyType()).isEqualTo(PenaltyMoneyType.FIXED_AMOUNT);
		assertThat(rule.getAmount()).isEqualTo(10_000L);
		assertThat(rule.getRatioPct()).isNull();
		assertThat(rule.getSetBy()).isEqualTo(HOST_ID);
		assertThat(rule.getUpdatedAt()).isEqualTo(SET_AT);
	}

	@ParameterizedTest
	@MethodSource("validValues")
	@DisplayName("종류에 맞는 경계값은 만들 수 있다")
	void createValid(PenaltyMoneyType moneyType, Long amount, Integer ratioPct) {
		PenaltyRule rule = create(moneyType, amount, ratioPct);

		assertThat(rule.getAmount()).isEqualTo(amount);
		assertThat(rule.getRatioPct()).isEqualTo(ratioPct);
	}

	static Stream<Arguments> validValues() {
		return Stream.of(
				Arguments.of(PenaltyMoneyType.FIXED_AMOUNT, 1L, null),
				Arguments.of(PenaltyMoneyType.FIXED_AMOUNT, 100_000_000L, null),
				Arguments.of(PenaltyMoneyType.RATIO, null, 1),
				Arguments.of(PenaltyMoneyType.RATIO, null, 100),
				Arguments.of(PenaltyMoneyType.FULL, null, null));
	}

	@ParameterizedTest
	@MethodSource("invalidValues")
	@DisplayName("종류와 값이 맞지 않거나 범위를 벗어나면 만들 수 없다")
	void createRejected(PenaltyMoneyType moneyType, Long amount, Integer ratioPct) {
		assertThatThrownBy(() -> create(moneyType, amount, ratioPct))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
	}

	static Stream<Arguments> invalidValues() {
		return Stream.of(
				Arguments.of(PenaltyMoneyType.FIXED_AMOUNT, null, null),
				Arguments.of(PenaltyMoneyType.FIXED_AMOUNT, 0L, null),
				Arguments.of(PenaltyMoneyType.FIXED_AMOUNT, 100_000_001L, null),
				Arguments.of(PenaltyMoneyType.FIXED_AMOUNT, 10_000L, 30),
				Arguments.of(PenaltyMoneyType.RATIO, null, null),
				Arguments.of(PenaltyMoneyType.RATIO, null, 0),
				Arguments.of(PenaltyMoneyType.RATIO, null, 101),
				Arguments.of(PenaltyMoneyType.RATIO, 10_000L, 30),
				Arguments.of(PenaltyMoneyType.FULL, 10_000L, null),
				Arguments.of(PenaltyMoneyType.FULL, null, 100));
	}

	@Test
	@DisplayName("새 회차로 옮기면 게임만 바뀌고 설정자와 설정 시각은 그대로다")
	void moveToKeepsSetByAndUpdatedAt() {
		GameSession original = game();
		GameSession rerun = game();
		PenaltyRule rule = PenaltyRule.create(original, PenaltyMoneyType.RATIO, null, 30, null, HOST_ID, SET_AT);

		rule.moveTo(rerun);

		assertThat(rule.getGameSession()).isSameAs(rerun);
		assertThat(rule.getSetBy()).isEqualTo(HOST_ID);
		assertThat(rule.getUpdatedAt()).isEqualTo(SET_AT);
		assertThat(rule.getMoneyType()).isEqualTo(PenaltyMoneyType.RATIO);
		assertThat(rule.getRatioPct()).isEqualTo(30);
	}

	private PenaltyRule create(PenaltyMoneyType moneyType, Long amount, Integer ratioPct) {
		return PenaltyRule.create(game(), moneyType, amount, ratioPct, null, HOST_ID, SET_AT);
	}

	private GameSession game() {
		return GameSession.create(null, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1);
	}
}
