package com.poco7250.moyeo.settlement.calc;

/**
 * 직전 확정 리비전 대비 회원 1명의 순잔액 변동(PRD 4.5 재확정 & 변동액 안내 규칙).
 *
 * @param previousNetBalance 직전 리비전 순잔액(행이 없으면 0)
 * @param currentNetBalance  새 리비전 순잔액(행이 없으면 0)
 * @param delta              currentNetBalance − previousNetBalance. +면 더 받고(덜 내고), −면 더 낸다
 */
public record NetBalanceDelta(long memberId, long previousNetBalance, long currentNetBalance, long delta) {
}
