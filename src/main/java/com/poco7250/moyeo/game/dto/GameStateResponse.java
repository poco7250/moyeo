package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import java.util.List;

/**
 * 게임 방 상태. API 응답과 실시간 메시지에 같이 쓴다.
 *
 * @param penaltyAmount 금액형 벌칙자 1인이 내는 금액(원). 금액형이 아니면 null
 * @param rootSessionId 다시 돌리기 이력의 첫 회차 id(다시 돌린 적 없으면 자기 id)
 * @param rerunOfId 이 회차가 다시 돌린 대상인 원 회차 id. 첫 회차거나 취소된 회차면 null
 * @param canceledRerunOfId 취소된 다시 돌린 회차가 가리키던 원 회차 id. 그 외에는 null
 * @param result 공개된 결과. 공개 전에는 null
 */
public record GameStateResponse(
		Long gameId,
		Long meetingId,
		GameType type,
		GameStatus status,
		Long createdBy,
		PenaltyKind penaltyKind,
		Long penaltyAmount,
		String penaltyNote,
		int penaltyCount,
		Long rootSessionId,
		Long rerunOfId,
		Long canceledRerunOfId,
		List<GameParticipantResponse> participants,
		GameResultResponse result) {

	/**
	 * 게임과 조회한 참가자·결과로 응답을 만든다.
	 */
	public static GameStateResponse of(GameSession game, List<GameParticipantResponse> participants,
			GameResultResponse result) {
		return new GameStateResponse(game.getId(), game.getMeeting().getId(), game.getType(), game.getStatus(),
				game.getCreatedBy(), game.getPenaltyKind(), game.getPenaltyAmount(), game.getPenaltyNote(),
				game.getPenaltyCount(),
				game.getRootSessionId(), idOf(game.getRerunOf()), idOf(game.getCanceledRerunOf()),
				participants, result);
	}

	private static Long idOf(GameSession game) {
		return game == null ? null : game.getId();
	}
}
