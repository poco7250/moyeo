package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferMinimizerTest {

	@Test
	@DisplayName("S-5: 예 1의 순잔액은 3건, 합 76,800원의 송금 안내가 되고 송금 후 전원 0이다")
	void minimizesExampleOne() {
		Map<Long, Long> balances = Map.of(1L, 76_800L, 2L, -30_000L, 3L, -23_400L, 4L, -23_400L);

		List<TransferGuide> transfers = TransferMinimizer.minimize(balances);

		assertThat(transfers).containsExactly(
				new TransferGuide(2L, 1L, 30_000L),
				new TransferGuide(3L, 1L, 23_400L),
				new TransferGuide(4L, 1L, 23_400L));
		assertThat(transfers.stream().mapToLong(TransferGuide::amount).sum()).isEqualTo(76_800L);
		assertThat(applyTransfers(balances, transfers).values()).containsOnly(0L);
	}

	@Test
	@DisplayName("S-5: 채권자·채무자가 섞여도 건수는 0 아닌 인원 − 1 이하이고 송금 후 전원 0이다")
	void boundsTransferCountForMixedBalances() {
		Map<Long, Long> balances = Map.of(1L, 50_000L, 2L, 30_000L, 3L, -45_000L, 4L, -20_000L, 5L, -15_000L, 6L, 0L);

		List<TransferGuide> transfers = TransferMinimizer.minimize(balances);

		assertThat(transfers).hasSizeLessThanOrEqualTo(4);
		assertThat(applyTransfers(balances, transfers).values()).containsOnly(0L);
	}

	@Test
	@DisplayName("입력 순서가 달라도 같은 결과가 나온다")
	void isDeterministic() {
		Map<Long, Long> first = new HashMap<>(Map.of(1L, 10_000L, 2L, 10_000L, 3L, -10_000L, 4L, -10_000L));
		Map<Long, Long> second = new TreeMap<>(Comparator.reverseOrder());
		second.putAll(first);

		assertThat(TransferMinimizer.minimize(first))
				.isEqualTo(TransferMinimizer.minimize(second))
				.containsExactly(new TransferGuide(3L, 1L, 10_000L), new TransferGuide(4L, 2L, 10_000L));
	}

	@Test
	@DisplayName("모두 0원이면 송금 안내가 없다")
	void returnsEmptyWhenSettled() {
		assertThat(TransferMinimizer.minimize(Map.of(1L, 0L, 2L, 0L))).isEmpty();
	}

	@Test
	@DisplayName("순잔액 합이 0이 아니면 예외가 난다")
	void rejectsUnbalancedInput() {
		assertThatThrownBy(() -> TransferMinimizer.minimize(Map.of(1L, 100L, 2L, -50L)))
				.isInstanceOf(IllegalStateException.class);
	}

	private static Map<Long, Long> applyTransfers(Map<Long, Long> balances, List<TransferGuide> transfers) {
		Map<Long, Long> result = new HashMap<>(balances);
		for (TransferGuide transfer : transfers) {
			result.merge(transfer.fromMemberId(), transfer.amount(), Long::sum);
			result.merge(transfer.toMemberId(), -transfer.amount(), Long::sum);
		}
		return result;
	}
}
