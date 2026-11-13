package com.poco7250.moyeo.settlement.calc;

/**
 * 규칙 하나에 걸린 벌칙자 1명. 정산에 반영되는(APPLIED) 벌칙만 넣는다.
 *
 * @param penaltyId 벌칙 id. 균등 분할 초과분을 차감하는 순서에 쓴다
 */
public record PenalizedMember(long penaltyId, long memberId) {
}
