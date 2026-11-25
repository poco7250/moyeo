package com.poco7250.moyeo.history.dto;

import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 지난 모임 한 건의 히스토리(언제·어디서·누가·얼마·무슨 게임, PRD H-1).
 *
 * @param attendeeCount 한 차수라도 참석한 인원(중복 없음)
 * @param totalExpenseAmount 현재 지출 합계(원), 지출이 없으면 0
 * @param settlementRevisionNo 마지막 확정 리비전 번호, 확정 전이면 null
 * @param games 모임의 게임(결과 공개된 것, 다시 돌린 게임은 마지막 회차만), 원 회차 순
 */
public record MeetingHistoryResponse(Long meetingId, String title, LocalDateTime startAt, int attendeeCount,
		long totalExpenseAmount, SettlementStatus settlementStatus, Integer settlementRevisionNo,
		List<RoundHistoryResponse> rounds, List<GameHistoryResponse> games) {
}
