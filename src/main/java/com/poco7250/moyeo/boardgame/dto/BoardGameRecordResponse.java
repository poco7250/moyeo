package com.poco7250.moyeo.boardgame.dto;

import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 보드게임 기록 한 건.
 *
 * @param ranks 순위 오름차순(같은 순위면 회원 id 순)
 * @param recordedByMemberId 기록한 회원 id
 */
public record BoardGameRecordResponse(Long id, Long roundId, String gameName, List<BoardGameRankResponse> ranks,
		Long recordedByMemberId, LocalDateTime createdAt, LocalDateTime updatedAt) {

	/**
	 * 기록 엔티티로 응답을 만든다. 차수는 LAZY 프록시의 id만 읽는다.
	 */
	public static BoardGameRecordResponse from(BoardGameRecord record) {
		return new BoardGameRecordResponse(record.getId(), record.getRound().getId(), record.getGameName(),
				record.getRanks().stream().map(BoardGameRankResponse::from).toList(), record.getRecordedBy(),
				record.getCreatedAt(), record.getUpdatedAt());
	}
}
