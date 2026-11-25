package com.poco7250.moyeo.settlement.calc;

/**
 * 금액형 게임 하나에 걸린 벌칙자 1명. 정산에 반영되는(APPLIED) 그룹 멤버 벌칙만 넣는다(게스트는 앱 밖에서 낸다).
 *
 * @param penaltyId 벌칙 id
 */
public record PenalizedMember(long penaltyId, long memberId) {
}
