package com.poco7250.moyeo.settlement.calc;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 두 리비전의 순잔액으로 1인당 변동액 Δ = N_new − N_prev를 구하는 순수 함수(PRD 4.5, 7장 설계 메모).
 * 변동액은 저장하지 않고 필요할 때마다 두 스냅샷에서 파생한다.
 */
public final class NetBalanceDeltaCalculator {

	private NetBalanceDeltaCalculator() {
	}

	/**
	 * 회원별 변동액을 회원 id 오름차순으로 돌려준다. 한쪽 리비전에만 있는 회원은 다른 쪽을 0으로 본다.
	 *
	 * @param previous 직전 리비전의 회원 id → 순잔액
	 * @param current  새 리비전의 회원 id → 순잔액
	 * @throws IllegalStateException 변동액 합이 0이 아니면(두 리비전 순잔액 합이 각각 0이라 나오면 계산 버그)
	 */
	public static List<NetBalanceDelta> calculate(Map<Long, Long> previous, Map<Long, Long> current) {
		// TreeSet이라 회원 id 오름차순으로 순회한다
		TreeSet<Long> memberIds = new TreeSet<>(previous.keySet());
		memberIds.addAll(current.keySet());
		List<NetBalanceDelta> deltas = memberIds.stream()
				.map(memberId -> delta(memberId, previous.getOrDefault(memberId, 0L),
						current.getOrDefault(memberId, 0L)))
				.toList();
		long sum = deltas.stream().mapToLong(NetBalanceDelta::delta).reduce(0L, Math::addExact);
		if (sum != 0) {
			throw new IllegalStateException("변동액 합이 0이 아닙니다: " + sum);
		}
		return deltas;
	}

	private static NetBalanceDelta delta(long memberId, long previousNetBalance, long currentNetBalance) {
		return new NetBalanceDelta(memberId, previousNetBalance, currentNetBalance,
				Math.subtractExact(currentNetBalance, previousNetBalance));
	}
}
