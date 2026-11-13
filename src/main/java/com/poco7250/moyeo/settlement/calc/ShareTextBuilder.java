package com.poco7250.moyeo.settlement.calc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 재확정 후 호스트가 단톡방에 붙여 넣을 공유 문구를 만드는 순수 함수(PRD 6장 알림(재확정)).
 * 예: "정산이 수정됐어요(리비전 2): 민수 −20,000원, 철수 +6,600원, … 이전 정산대로 이미 송금했다면 …"
 */
public final class ShareTextBuilder {

	static final String UNKNOWN_NAME = "알 수 없음";
	// PRD 표기를 따라 마이너스는 U+2212(−)를 쓴다
	private static final char MINUS = '−';

	private ShareTextBuilder() {
	}

	/**
	 * 변동액이 0이 아닌 회원만 변동액 오름차순(더 내야 하는 사람 먼저, 같으면 회원 id 순)으로 나열한다.
	 *
	 * @param nicknames 회원 id → 그룹 닉네임. 없으면 "알 수 없음"
	 * @param notice    문구 끝에 붙일 고정 안내 문구
	 */
	public static String build(int revisionNo, List<NetBalanceDelta> deltas, Map<Long, String> nicknames,
			String notice) {
		List<NetBalanceDelta> changed = deltas.stream()
				.filter(delta -> delta.delta() != 0)
				.sorted(Comparator.comparingLong(NetBalanceDelta::delta).thenComparingLong(NetBalanceDelta::memberId))
				.toList();
		if (changed.isEmpty()) {
			return "정산이 다시 확정됐어요(리비전 " + revisionNo + "). 1인당 변동액은 없어요.";
		}
		String items = changed.stream()
				.map(delta -> nicknames.getOrDefault(delta.memberId(), UNKNOWN_NAME) + " " + signedWon(delta.delta()))
				.collect(Collectors.joining(", "));
		return "정산이 수정됐어요(리비전 " + revisionNo + "): " + items + ". " + notice;
	}

	// 부호와 천 단위 구분 쉼표를 붙인 금액. Locale에 따라 바뀌지 않게 직접 만든다
	static String signedWon(long amount) {
		char sign = amount < 0 ? MINUS : '+';
		String digits = Long.toString(Math.absExact(amount));
		StringBuilder grouped = new StringBuilder();
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (digits.length() - i) % 3 == 0) {
				grouped.append(',');
			}
			grouped.append(digits.charAt(i));
		}
		return sign + grouped.toString() + "원";
	}
}
