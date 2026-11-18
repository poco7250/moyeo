package com.poco7250.moyeo.history.dto;

/**
 * 보드게임 꼴등 순위 한 줄. 꼴등이 지정된 기록만 센다.
 *
 * @param rank 꼴등 횟수 기준 순위(동점이면 같은 순위, 예: 1, 1, 3)
 * @param loserCount 꼴등(전액 부담) 횟수
 * @param playCount 참가 횟수
 */
public record BoardGameLoserRankResponse(int rank, HistoryMemberResponse member, long loserCount, long playCount) {
}
