package com.poco7250.moyeo.settlement.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import java.time.LocalDateTime;

/**
 * 변경 로그 1건 응답(PRD S-11 — 누가·언제·무엇을·전→후).
 *
 * @param actorId 변경한 회원. 스케줄러가 일으킨 변경이면 null
 * @param before  변경 전 값(JSON 객체 그대로). 생성이면 null
 * @param after   변경 후 값(JSON 객체 그대로). 삭제면 null
 */
public record ChangeLogResponse(
		Long id,
		Long actorId,
		LocalDateTime changedAt,
		ChangeLogEntityType entityType,
		Long entityId,
		ChangeType changeType,
		// 저장된 값이 이미 JSON이라 문자열로 한 번 더 감싸지 않고 그대로 내보낸다
		@JsonRawValue String before,
		@JsonRawValue String after,
		SettlementStatus settlementStatusAfter) {

	/**
	 * 저장된 변경 로그를 응답으로 변환한다.
	 */
	public static ChangeLogResponse from(ChangeLog log) {
		return new ChangeLogResponse(log.getId(), log.getActorId(), log.getChangedAt(), log.getEntityType(),
				log.getEntityId(), log.getChangeType(), log.getBeforeJson(), log.getAfterJson(),
				log.getSettlementStatusAfter());
	}
}
