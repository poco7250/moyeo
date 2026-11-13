package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;

/**
 * 리비전에 고정된 벌칙 부담액과 그 시점 조건 스냅샷. 대상 지출이 나중에 삭제돼도 값이 남는다.
 *
 * @param fixedAmount 고정 금액 조건일 때만 값이 있다
 * @param ratioPct    비율 조건일 때만 값이 있다
 */
public record RevisionPenaltyChargeResponse(Long penaltyId, Long memberId, long amount, PenaltyMoneyType moneyType,
		Long fixedAmount, Integer ratioPct, Long targetExpenseId, long targetExpenseAmount, String targetExpenseMemo) {

	/**
	 * 저장된 스냅샷을 응답으로 변환한다. 벌칙은 함께 읽어 둔 상태여야 한다.
	 */
	public static RevisionPenaltyChargeResponse from(PenaltyCharge charge) {
		return new RevisionPenaltyChargeResponse(charge.getPenalty().getId(), charge.getPenalty().getMember().getId(),
				charge.getAmount(), charge.getMoneyType(), charge.getFixedAmount(), charge.getRatioPct(),
				charge.getTargetExpenseId(), charge.getTargetExpenseAmount(), charge.getTargetExpenseMemo());
	}
}
