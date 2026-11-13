package com.poco7250.moyeo.settlement.calc;

import java.util.List;

/**
 * 지출 1건에서 벌칙 부담액을 뗀 결과.
 *
 * @param charges   적용 순서대로 나열한 벌칙별 부담액
 * @param remaining 벌칙을 떼고 남은 금액. 벌칙자를 뺀 참석자가 나눈다
 */
public record PenaltyDeduction(List<PenaltyChargeResult> charges, long remaining) {
}
