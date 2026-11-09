package com.poco7250.moyeo.settlement.calc;

/**
 * 회원 한 명의 분배 조정. 한 사람당 제외·고정 금액·가중치 중 하나만 쓴다(API 입력에서 검사).
 *
 * @param fixedAmount 고정 금액(원). 없으면 null
 * @param weight      분배 가중치. 없으면 null이고 계산에서는 1로 본다
 */
public record ShareAdjustment(long memberId, boolean excluded, Long fixedAmount, Integer weight) {
}
