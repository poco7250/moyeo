package com.poco7250.moyeo.history.dto;

/**
 * 연말 결산 부담액 순위 한 줄.
 *
 * @param rank 부담액 기준 순위(동점이면 같은 순위)
 * @param shareAmount 모임마다 마지막 확정 리비전의 부담액 합(원). 확정 전 모임은 빠진다
 */
public record ShareRankResponse(int rank, HistoryMemberResponse member, long shareAmount) {
}
