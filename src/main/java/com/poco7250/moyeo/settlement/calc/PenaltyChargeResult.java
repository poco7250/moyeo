package com.poco7250.moyeo.settlement.calc;

/**
 * 벌칙 1건의 부담액. 저장하지 않고 정산을 계산할 때마다 새로 구한다.
 *
 * @param gameId 벌칙이 나온 게임 id
 * @param amount 부담액(원). 나눠 받을 사람이 없으면 0이다
 */
public record PenaltyChargeResult(long penaltyId, long memberId, long gameId, long amount) {
}
