package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import java.util.List;

/**
 * 게임 방 상태. API 응답과 실시간 메시지에 같이 쓴다.
 *
 * @param result 공개된 결과. 공개 전에는 null
 */
public record GameStateResponse(
		Long gameId,
		Long roundId,
		GameType type,
		GameStatus status,
		Long createdBy,
		PenaltyKind penaltyKind,
		String penaltyNote,
		int penaltyCount,
		List<GameParticipantResponse> participants,
		GameResultResponse result) {

	/**
	 * 게임과 조회한 참가자·결과로 응답을 만든다.
	 */
	public static GameStateResponse of(GameSession game, List<GameParticipantResponse> participants,
			GameResultResponse result) {
		return new GameStateResponse(game.getId(), game.getRound().getId(), game.getType(), game.getStatus(),
				game.getCreatedBy(), game.getPenaltyKind(), game.getPenaltyNote(), game.getPenaltyCount(),
				participants, result);
	}
}
