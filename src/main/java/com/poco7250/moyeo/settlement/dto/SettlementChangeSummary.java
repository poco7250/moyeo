package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;

/**
 * "재확정 필요" 이벤트에 싣는 변경 요약. 전/후 값은 변경 로그에서 본다.
 *
 * @param entityType 바뀐 대상 종류
 * @param entityId 바뀐 대상 id
 * @param changeType 변경 종류
 */
public record SettlementChangeSummary(ChangeLogEntityType entityType, Long entityId, ChangeType changeType) {
}
