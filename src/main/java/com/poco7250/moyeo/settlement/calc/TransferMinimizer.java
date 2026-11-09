package com.poco7250.moyeo.settlement.calc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 순잔액을 송금 안내 목록으로 바꾼다(PRD S-5).
 * 가장 많이 받을 사람과 가장 많이 낼 사람을 반복해서 맞춘다. 금액이 같으면 memberId가 작은 쪽이 먼저다.
 * 건수는 (순잔액이 0이 아닌 인원 − 1) 이하이고, 같은 입력이면 항상 같은 결과가 나온다.
 */
public final class TransferMinimizer {

	private static final Comparator<Balance> LARGEST_FIRST =
			Comparator.comparingLong(Balance::amount).reversed().thenComparingLong(Balance::memberId);

	private TransferMinimizer() {
	}

	/**
	 * 회원 id별 순잔액으로 송금 안내 목록을 만든다.
	 *
	 * @throws IllegalStateException 순잔액 합이 0이 아니면
	 */
	public static List<TransferGuide> minimize(Map<Long, Long> netBalances) {
		PriorityQueue<Balance> creditors = new PriorityQueue<>(LARGEST_FIRST);
		PriorityQueue<Balance> debtors = new PriorityQueue<>(LARGEST_FIRST);
		long total = 0L;
		for (Map.Entry<Long, Long> entry : netBalances.entrySet()) {
			total = Math.addExact(total, entry.getValue());
			if (entry.getValue() > 0) {
				creditors.add(new Balance(entry.getKey(), entry.getValue()));
			} else if (entry.getValue() < 0) {
				debtors.add(new Balance(entry.getKey(), Math.negateExact(entry.getValue())));
			}
		}
		if (total != 0L) {
			throw new IllegalStateException("순잔액 합이 0이 아닙니다: " + total);
		}
		return match(creditors, debtors);
	}

	private static List<TransferGuide> match(PriorityQueue<Balance> creditors, PriorityQueue<Balance> debtors) {
		List<TransferGuide> transfers = new ArrayList<>();
		while (!creditors.isEmpty()) {
			Balance creditor = creditors.poll();
			Balance debtor = debtors.poll();
			long amount = Math.min(creditor.amount(), debtor.amount());
			transfers.add(new TransferGuide(debtor.memberId(), creditor.memberId(), amount));
			requeueRemainder(creditor, amount, creditors);
			requeueRemainder(debtor, amount, debtors);
		}
		return transfers;
	}

	private static void requeueRemainder(Balance balance, long settled, PriorityQueue<Balance> queue) {
		if (balance.amount() > settled) {
			queue.add(new Balance(balance.memberId(), balance.amount() - settled));
		}
	}

	private record Balance(long memberId, long amount) {
	}
}
