package com.poco7250.moyeo.penalty.dto;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import java.time.LocalDateTime;

/**
 * 변경 로그에 남기는 벌칙 금액 조건 값.
 *
 * @param gameSessionId 조건이 걸린 게임 회차 id. 다시 돌리기로 이관되면 이 값만 바뀐다
 * @param setBy 조건을 정한 회원 id
 * @param updatedAt 조건을 정한 시각
 */
public record PenaltyRuleSnapshot(Long gameSessionId, PenaltyMoneyType moneyType, Long amount, Integer ratioPct,
		Long targetExpenseId, Long setBy, LocalDateTime updatedAt) implements ChangeLogSnapshot {

	/**
	 * 금액 조건의 현재 값으로 스냅샷을 만든다.
	 */
	public static PenaltyRuleSnapshot from(PenaltyRule rule) {
		return new PenaltyRuleSnapshot(rule.getGameSession().getId(), rule.getMoneyType(), rule.getAmount(),
				rule.getRatioPct(), rule.getTargetExpense().getId(), rule.getSetBy(), rule.getUpdatedAt());
	}
}
