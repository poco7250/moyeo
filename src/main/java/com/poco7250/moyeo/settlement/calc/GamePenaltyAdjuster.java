package com.poco7250.moyeo.settlement.calc;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 금액형 게임 벌칙을 회원별 부담액에 반영한다(PRD 4.5 벌칙 정산 규칙, 2026-11-24).
 * 지출 분배가 끝난 부담액에 적용하므로 지출과 무관하게 동작하고, 부담액 합계는 바뀌지 않는다(순잔액 합 0 유지).
 */
public final class GamePenaltyAdjuster {

	private GamePenaltyAdjuster() {
	}

	/**
	 * 벌칙자마다 부담액에 금액을 더하고, 합계를 나눠 받는 사람에게서 똑같이 뺀다.
	 * 1인 몫은 합계 ÷ 인원(버림)이고, 나머지 원은 memberId가 작은 사람부터 1원씩 더 뺀다.
	 * 나눠 받을 사람이 없으면 아무도 부담하지 않고 0원으로 돌려준다(표시만).
	 *
	 * @param shares 회원별 부담액(원). 이 메서드가 고친다
	 * @return 벌칙별 부담액
	 * @throws IllegalArgumentException 나눠 받는 사람에 벌칙자가 섞여 있으면(조립 버그)
	 */
	public static List<PenaltyChargeResult> apply(GamePenaltyInput game, Map<Long, Long> shares) {
		requireDisjoint(game);
		if (game.creditedMemberIds().isEmpty() || game.penalized().isEmpty()) {
			return charges(game, 0L);
		}
		game.penalized().forEach(member -> shares.merge(member.memberId(), game.amount(), Math::addExact));
		long total = Math.multiplyExact(game.amount(), game.penalized().size());
		creditEvenly(total, new TreeSet<>(game.creditedMemberIds()), shares);
		return charges(game, game.amount());
	}

	private static void creditEvenly(long total, TreeSet<Long> creditedIds, Map<Long, Long> shares) {
		long count = creditedIds.size();
		long each = total / count;
		long remainder = total % count;
		for (Long memberId : creditedIds) {
			long credit = remainder-- > 0 ? each + 1 : each;
			shares.merge(memberId, -credit, Math::addExact);
		}
	}

	private static List<PenaltyChargeResult> charges(GamePenaltyInput game, long amount) {
		return game.penalized().stream()
				.map(member -> new PenaltyChargeResult(member.penaltyId(), member.memberId(), game.gameId(), amount))
				.toList();
	}

	private static void requireDisjoint(GamePenaltyInput game) {
		boolean overlaps = game.penalized().stream()
				.anyMatch(member -> game.creditedMemberIds().contains(member.memberId()));
		if (overlaps) {
			throw new IllegalArgumentException("벌칙자가 나눠 받는 사람에 들어 있습니다: game=" + game.gameId());
		}
	}
}
