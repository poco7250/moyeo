package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetBalanceDeltaCalculatorTest {

	private static final long CHULSU = 1L;
	private static final long MINSU = 2L;
	private static final long YOUNGHEE = 3L;
	private static final long JISU = 4L;

	@Test
	@DisplayName("예 6: 리비전 1 → 2 변동액은 철수 +6,600 / 민수 −20,000 / 영희 +6,700 / 지수 +6,700이고 합은 0이다")
	void example6() {
		Map<Long, Long> revision1 = Map.of(CHULSU, 76_800L, MINSU, -30_000L, YOUNGHEE, -23_400L, JISU, -23_400L);
		Map<Long, Long> revision2 = Map.of(CHULSU, 83_400L, MINSU, -50_000L, YOUNGHEE, -16_700L, JISU, -16_700L);

		assertThat(NetBalanceDeltaCalculator.calculate(revision1, revision2)).containsExactly(
				new NetBalanceDelta(CHULSU, 76_800L, 83_400L, 6_600L),
				new NetBalanceDelta(MINSU, -30_000L, -50_000L, -20_000L),
				new NetBalanceDelta(YOUNGHEE, -23_400L, -16_700L, 6_700L),
				new NetBalanceDelta(JISU, -23_400L, -16_700L, 6_700L));
	}

	@Test
	@DisplayName("한쪽 리비전에만 있는 회원은 다른 쪽을 0으로 보고 회원 id 오름차순으로 돌려준다")
	void missingMemberIsZero() {
		Map<Long, Long> previous = Map.of(CHULSU, 20_000L, MINSU, -10_000L, YOUNGHEE, -10_000L);
		Map<Long, Long> current = Map.of(JISU, -15_000L, CHULSU, 15_000L);

		assertThat(NetBalanceDeltaCalculator.calculate(previous, current)).containsExactly(
				new NetBalanceDelta(CHULSU, 20_000L, 15_000L, -5_000L),
				new NetBalanceDelta(MINSU, -10_000L, 0L, 10_000L),
				new NetBalanceDelta(YOUNGHEE, -10_000L, 0L, 10_000L),
				new NetBalanceDelta(JISU, 0L, -15_000L, -15_000L));
	}

	@Test
	@DisplayName("결과가 같은 재확정이면 전원 변동액 0원이다")
	void sameResultAllZero() {
		Map<Long, Long> balances = Map.of(CHULSU, 10_000L, MINSU, -10_000L);

		assertThat(NetBalanceDeltaCalculator.calculate(balances, balances))
				.extracting(NetBalanceDelta::delta)
				.containsExactly(0L, 0L);
	}

	@Test
	@DisplayName("두 리비전이 모두 비어 있으면 빈 목록이다")
	void empty() {
		assertThat(NetBalanceDeltaCalculator.calculate(Map.of(), Map.of())).isEmpty();
	}

	@Test
	@DisplayName("변동액 합이 0이 아니면 계산 버그로 보고 IllegalStateException을 던진다")
	void nonZeroSumRejected() {
		assertThatThrownBy(() -> NetBalanceDeltaCalculator.calculate(Map.of(CHULSU, 10_000L), Map.of(CHULSU, 0L)))
				.isInstanceOf(IllegalStateException.class);
	}
}
