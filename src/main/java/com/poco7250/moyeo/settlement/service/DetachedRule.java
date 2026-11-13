package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;

/**
 * 대상 지출 삭제로 함께 지운 벌칙 금액 조건 1건.
 *
 * @param ruleId 지운 조건 id(변경 로그 대상 id)
 * @param gameId 조건이 걸려 있던 게임 id. 이 게임의 벌칙이 ASSIGNED로 돌아갔다
 * @param before 지우기 전 값
 */
record DetachedRule(Long ruleId, Long gameId, PenaltyRuleSnapshot before) {
}
