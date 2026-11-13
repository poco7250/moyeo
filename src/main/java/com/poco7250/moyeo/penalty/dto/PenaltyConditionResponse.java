package com.poco7250.moyeo.penalty.dto;

import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 게임 단위 벌칙 조건 상태. 조건 설정·해제·면제·면제 취소 뒤의 결과를 돌려준다.
 *
 * @param status 그 게임의 비무효 벌칙 전원이 함께 가진 상태
 * @param rule 금액 조건. APPLIED가 아니면 null
 * @param waivedBy 면제한 회원 id. WAIVED가 아니면 null
 * @param waivedAt 면제한 시각. WAIVED가 아니면 null
 * @param penaltyMemberIds 벌칙자 회원 id
 */
public record PenaltyConditionResponse(Long gameId, PenaltyStatus status, PenaltyRuleResponse rule, Long waivedBy,
		LocalDateTime waivedAt, List<Long> penaltyMemberIds) {
}
