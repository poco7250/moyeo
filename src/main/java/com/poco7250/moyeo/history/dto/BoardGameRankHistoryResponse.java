package com.poco7250.moyeo.history.dto;

/**
 * 히스토리 보드게임 기록의 참가자 한 명 순위.
 */
public record BoardGameRankHistoryResponse(int rank, HistoryMemberResponse member) {
}
