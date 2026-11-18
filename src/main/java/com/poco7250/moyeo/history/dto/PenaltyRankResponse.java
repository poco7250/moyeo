package com.poco7250.moyeo.history.dto;

/**
 * 벌칙 순위 한 줄.
 *
 * @param rank 당첨 횟수 기준 순위(동점이면 같은 순위, 예: 1, 1, 3)
 * @param penaltyCount 무효가 아닌 벌칙 당첨 횟수(금액형·행동형·면제 포함)
 * @param penaltyAmount 모임마다 마지막 확정 리비전의 벌칙 부담액 합(원). 확정 전 모임은 빠진다
 */
public record PenaltyRankResponse(int rank, HistoryMemberResponse member, long penaltyCount, long penaltyAmount) {
}
