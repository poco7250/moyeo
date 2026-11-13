package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShareTextBuilderTest {

	private static final String NOTICE = "이미 송금했다면 변동분만 주고받으세요.";
	private static final long CHULSU = 1L;
	private static final long MINSU = 2L;
	private static final long YOUNGHEE = 3L;
	private static final long JISU = 4L;
	private static final Map<Long, String> NAMES = Map.of(CHULSU, "철수", MINSU, "민수", YOUNGHEE, "영희", JISU, "지수");

	@Test
	@DisplayName("예 6: 변동액 오름차순(더 낼 사람 먼저)으로 부호·천 단위 구분을 붙여 나열하고 안내 문구를 붙인다")
	void example6() {
		List<NetBalanceDelta> deltas = List.of(
				new NetBalanceDelta(CHULSU, 76_800L, 83_400L, 6_600L),
				new NetBalanceDelta(MINSU, -30_000L, -50_000L, -20_000L),
				new NetBalanceDelta(YOUNGHEE, -23_400L, -16_700L, 6_700L),
				new NetBalanceDelta(JISU, -23_400L, -16_700L, 6_700L));

		assertThat(ShareTextBuilder.build(2, deltas, NAMES, NOTICE)).isEqualTo(
				"정산이 수정됐어요(리비전 2): 민수 −20,000원, 철수 +6,600원, 영희 +6,700원, 지수 +6,700원. " + NOTICE);
	}

	@Test
	@DisplayName("변동액 0인 회원은 빼고, 닉네임이 없는 회원은 '알 수 없음'으로 쓴다")
	void skipsZeroAndUnknownName() {
		List<NetBalanceDelta> deltas = List.of(
				new NetBalanceDelta(CHULSU, 10_000L, 10_000L, 0L),
				new NetBalanceDelta(MINSU, -10_000L, -15_000L, -5_000L),
				new NetBalanceDelta(99L, 0L, 5_000L, 5_000L));

		assertThat(ShareTextBuilder.build(3, deltas, NAMES, NOTICE))
				.isEqualTo("정산이 수정됐어요(리비전 3): 민수 −5,000원, 알 수 없음 +5,000원. " + NOTICE);
	}

	@Test
	@DisplayName("전원 변동액이 0이면 변동 없음 문구를 준다")
	void allZero() {
		List<NetBalanceDelta> deltas = List.of(
				new NetBalanceDelta(CHULSU, 10_000L, 10_000L, 0L),
				new NetBalanceDelta(MINSU, -10_000L, -10_000L, 0L));

		assertThat(ShareTextBuilder.build(2, deltas, NAMES, NOTICE))
				.isEqualTo("정산이 다시 확정됐어요(리비전 2). 1인당 변동액은 없어요.");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"1|+1원",
			"999|+999원",
			"1000|+1,000원",
			"-100000|−100,000원",
			"1234567|+1,234,567원",
			"-100000000|−100,000,000원"})
	@DisplayName("금액은 부호(+, −)와 세 자리 쉼표를 붙인다")
	void signedWon(long amount, String expected) {
		assertThat(ShareTextBuilder.signedWon(amount)).isEqualTo(expected);
	}
}
