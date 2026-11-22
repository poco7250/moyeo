package com.poco7250.moyeo.history.dto;

/**
 * 연말 결산 참석 순위 한 줄.
 *
 * @param rank 참석 횟수 기준 순위(동점이면 같은 순위, 예: 1, 1, 3)
 * @param attendCount 한 차수라도 참석(ATTEND)한 모임 수
 */
public record AttendanceRankResponse(int rank, HistoryMemberResponse member, long attendCount) {
}
