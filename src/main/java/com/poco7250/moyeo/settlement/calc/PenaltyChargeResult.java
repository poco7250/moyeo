package com.poco7250.moyeo.settlement.calc;

/**
 * 벌칙 1건의 부담액. 저장하지 않고 정산을 계산할 때마다 새로 구한다.
 *
 * @param expenseId 대상 지출 id
 * @param amount    부담액(원). 앞선 벌칙이 잔액을 다 쓰면 0일 수 있다
 */
public record PenaltyChargeResult(long penaltyId, long memberId, long expenseId, long amount) {
}
