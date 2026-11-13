package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PenaltyWarningEvaluatorTest {

	private static final LocalDateTime ORDER = LocalDateTime.of(2026, 11, 13, 20, 0);

	private static PenaltyRuleInput rule(PenaltyMoneyType type, Long amount, Integer pct, int penalizedCount) {
		List<PenalizedMember> members = LongStream.rangeClosed(1, penalizedCount)
				.mapToObj(id -> new PenalizedMember(id, id + 1))
				.toList();
		return new PenaltyRuleInput(31L, type, amount, pct, ORDER, 1L, members);
	}

	private static PenaltyRuleInput fixed(long amount, int count) {
		return rule(PenaltyMoneyType.FIXED_AMOUNT, amount, null, count);
	}

	private static PenaltyRuleInput ratio(int pct, int count) {
		return rule(PenaltyMoneyType.RATIO, null, pct, count);
	}

	private static PenaltyRuleInput full(int count) {
		return rule(PenaltyMoneyType.FULL, null, null, count);
	}

	@Test
	@DisplayName("규칙이 없으면 경고가 없다")
	void noRules() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(100_000L, List.of())).isFalse();
	}

	@Test
	@DisplayName("예 1: RATIO 30% 한 명은 경고가 없다")
	void singleRatio() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(100_000L, List.of(ratio(30, 1)))).isFalse();
	}

	@Test
	@DisplayName("예 3: 고정 금액 한 건이 지출 금액을 넘으면 경고다")
	void fixedOverTarget() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(100_000L, List.of(fixed(150_000L, 1)))).isTrue();
	}

	@Test
	@DisplayName("고정 금액이 지출 금액과 같으면 경고가 없다")
	void fixedEqualsTarget() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(100_000L, List.of(fixed(100_000L, 1)))).isFalse();
	}

	@Test
	@DisplayName("예 4-a: FULL 한 명은 경고가 없다")
	void singleFull() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(full(1)))).isFalse();
	}

	@Test
	@DisplayName("예 4-b: RATIO 50% 두 규칙은 올림 전 합이 지출 금액과 같아 경고가 없다")
	void twoHalfRatios() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(ratio(50, 1), ratio(50, 1)))).isFalse();
	}

	@Test
	@DisplayName("예 5-a: RATIO 30% 벌칙자 2명은 경고가 없다")
	void ratioTwoMembers() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(ratio(30, 2)))).isFalse();
	}

	@Test
	@DisplayName("예 5-b: 고정 50,000원과 RATIO 30% 2명의 합이 지출 금액을 넘으면 경고다")
	void rulesTogetherOverTarget() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(fixed(50_000L, 1), ratio(30, 2)))).isTrue();
	}

	@Test
	@DisplayName("m4: FULL 벌칙자 2명처럼 인원수 때문에만 넘으면 경고가 없다")
	void fullTwoMembers() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(full(2)))).isFalse();
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(ratio(60, 3)))).isFalse();
	}

	@Test
	@DisplayName("인원수로 넘는 규칙에 다른 규칙이 더해지면 경고다")
	void splitRulePlusAnother() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(87_650L, List.of(full(2), fixed(1L, 1)))).isTrue();
	}

	@Test
	@DisplayName("1억 원 지출에 규칙이 여러 개여도 오버플로 없이 판단한다")
	void maxAmounts() {
		assertThat(PenaltyWarningEvaluator.exceedsTarget(100_000_000L,
				List.of(fixed(100_000_000L, 50), ratio(100, 50), full(50)))).isTrue();
	}
}
