package com.poco7250.moyeo.history.dto;

/**
 * 노쇼 순위 한 줄(PRD M-8).
 *
 * @param rank 노쇼 횟수 기준 순위(동점이면 같은 순위, 예: 1, 1, 3)
 * @param noShowCount 한 차수라도 노쇼였던 모임 수
 */
public record NoShowRankResponse(int rank, HistoryMemberResponse member, long noShowCount) {
}
