package com.poco7250.moyeo.settlement.calc;

import java.util.List;
import java.util.Set;

/**
 * 금액형 게임 하나의 정산 입력(PRD 4.5 벌칙 정산 규칙, 2026-11-24).
 * 벌칙자는 각자 amount를 더 내고, 걷힌 돈은 나눠 받는 사람끼리 똑같이 몫을 줄인다.
 *
 * @param amount            벌칙자 1인이 내는 금액(원, 1 이상)
 * @param penalized         정산에 반영할 벌칙자(그룹 멤버, APPLIED)
 * @param creditedMemberIds 나눠 받는 사람 = 게임 참가자 − 벌칙자 − 게스트. 벌칙자와 겹치면 안 된다
 */
public record GamePenaltyInput(long gameId, long amount, List<PenalizedMember> penalized,
		Set<Long> creditedMemberIds) {

	public GamePenaltyInput {
		penalized = List.copyOf(penalized);
		creditedMemberIds = Set.copyOf(creditedMemberIds);
	}
}
