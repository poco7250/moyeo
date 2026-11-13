package com.poco7250.moyeo.penalty.dto;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import java.time.LocalDateTime;

/**
 * 게임에 걸린 벌칙 금액 조건.
 *
 * @param amount 고정 금액일 때만 값이 있다(원)
 * @param ratioPct 비율일 때만 값이 있다(%)
 * @param setBy 조건을 정한 회원 id
 * @param updatedAt 조건을 정한 시각
 */
public record PenaltyRuleResponse(PenaltyMoneyType moneyType, Long amount, Integer ratioPct, Long targetExpenseId,
		Long setBy, LocalDateTime updatedAt) {

	/**
	 * 금액 조건 엔티티를 응답으로 바꾼다.
	 */
	public static PenaltyRuleResponse from(PenaltyRule rule) {
		return new PenaltyRuleResponse(rule.getMoneyType(), rule.getAmount(), rule.getRatioPct(),
				rule.getTargetExpense().getId(), rule.getSetBy(), rule.getUpdatedAt());
	}
}
