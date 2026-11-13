package com.poco7250.moyeo.settlement.calc;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 계산에 쓰는 벌칙 금액 조건 1건(게임당 1개).
 *
 * @param amount        고정 금액(원). FIXED_AMOUNT가 아니면 null
 * @param ratioPct      비율(1~100 정수 %). RATIO가 아니면 null
 * @param applyOrderAt  게임 순번(결과가 처음 공개된 시각). 다시 돌린 회차는 원 회차 값을 이어받는다
 * @param rootSessionId 원 회차 게임 id. 순번이 같을 때 순서를 가른다
 * @param penalized     이 규칙의 APPLIED 벌칙자. ASSIGNED·WAIVED·VOIDED 벌칙자는 넣지 않는다
 */
public record PenaltyRuleInput(
		long targetExpenseId,
		PenaltyMoneyType moneyType,
		Long amount,
		Integer ratioPct,
		LocalDateTime applyOrderAt,
		long rootSessionId,
		List<PenalizedMember> penalized) {
}
