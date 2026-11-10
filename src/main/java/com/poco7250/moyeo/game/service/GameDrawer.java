package com.poco7250.moyeo.game.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 벌칙자를 뽑는 순수 함수. 같은 참가자·seed면 항상 같은 결과가 나온다.
 */
public final class GameDrawer {

	private GameDrawer() {
	}

	/**
	 * 참가자 중 벌칙자를 뽑는다. 입력 순서에 영향받지 않도록 회원 id로 정렬한 뒤 섞는다.
	 *
	 * @return 벌칙자 회원 id
	 */
	public static Set<Long> draw(Collection<Long> memberIds, int penaltyCount, long seed) {
		List<Long> shuffled = new ArrayList<>(memberIds);
		Collections.sort(shuffled);
		Collections.shuffle(shuffled, new Random(seed));
		return Set.copyOf(shuffled.subList(0, penaltyCount));
	}
}
