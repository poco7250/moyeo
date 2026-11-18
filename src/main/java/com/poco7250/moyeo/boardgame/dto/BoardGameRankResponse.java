package com.poco7250.moyeo.boardgame.dto;

import com.poco7250.moyeo.boardgame.domain.BoardGameRank;

/**
 * 보드게임 기록의 참가자 한 명 순위.
 */
public record BoardGameRankResponse(Long memberId, int rank) {

	/**
	 * 도메인 순위로 응답을 만든다.
	 */
	public static BoardGameRankResponse from(BoardGameRank rank) {
		return new BoardGameRankResponse(rank.getMemberId(), rank.getRankNo());
	}
}
