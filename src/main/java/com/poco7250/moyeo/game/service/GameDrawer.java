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
		return Set.copyOf(order(memberIds, seed).subList(0, penaltyCount));
	}

	/**
	 * 참가자 전원의 순서를 정한다. 입력 순서에 영향받지 않도록 회원 id로 정렬한 뒤 섞는다.
	 * 벌칙자 추첨은 이 순서의 앞에서부터 고른다.
	 *
	 * @return 1번부터 차례로 놓인 회원 id
	 */
	public static List<Long> order(Collection<Long> memberIds, long seed) {
		List<Long> shuffled = new ArrayList<>(memberIds);
		Collections.sort(shuffled);
		Collections.shuffle(shuffled, new Random(seed));
		return List.copyOf(shuffled);
	}
}
