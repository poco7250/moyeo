package com.poco7250.moyeo.settlement.dto;

/**
 * "재확정 필요" 실시간 이벤트 본문.
 *
 * @param actorId 변경한 회원. 스케줄러가 일으킨 변경이면 null
 */
public record SettlementReopenedResponse(Long actorId) {
}
