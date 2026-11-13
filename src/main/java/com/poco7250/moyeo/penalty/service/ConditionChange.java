package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;
import com.poco7250.moyeo.penalty.dto.PenaltyWaiverSnapshot;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;

/**
 * 벌칙 조건 변경 한 건. 변경 로그 한 건과 "재확정 필요" 이벤트의 변경 요약 한 건이 된다.
 *
 * @param before 변경 전 값. 생성이면 null
 * @param after 변경 후 값. 삭제면 null
 */
record ConditionChange(ChangeLogEntityType entityType, Long entityId, ChangeType changeType,
		ChangeLogSnapshot before, ChangeLogSnapshot after) {

	/**
	 * 금액 조건의 설정·변경·해제.
	 */
	static ConditionChange ofRule(Long ruleId, ChangeType changeType, PenaltyRuleSnapshot before,
			PenaltyRuleSnapshot after) {
		return new ConditionChange(ChangeLogEntityType.PENALTY_RULE, ruleId, changeType, before, after);
	}

	/**
	 * 게임 단위 면제·면제 취소.
	 */
	static ConditionChange ofWaiver(Long gameId, PenaltyWaiverSnapshot before, PenaltyWaiverSnapshot after) {
		return new ConditionChange(ChangeLogEntityType.PENALTY, gameId, ChangeType.UPDATE, before, after);
	}

	SettlementChangeSummary summary() {
		return new SettlementChangeSummary(entityType, entityId, changeType);
	}
}
