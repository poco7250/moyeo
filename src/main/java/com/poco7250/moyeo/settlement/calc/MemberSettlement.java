package com.poco7250.moyeo.settlement.calc;

/**
 * 회원별 정산 결과.
 *
 * @param shareAmount 부담액 합계
 * @param netBalance  순잔액(받을 돈은 +, 낼 돈은 −)
 */
public record MemberSettlement(long memberId, long shareAmount, long netBalance) {
}
