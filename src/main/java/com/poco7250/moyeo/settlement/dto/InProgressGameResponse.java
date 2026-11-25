package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;

/**
 * 정산 확정을 막은 대기·진행 중 금액형 게임 1건(PRD S-10). 공개하거나 취소해야 확정할 수 있다.
 *
 * @param penaltyAmount 벌칙자 1인 금액(원). 금액 없는 옛 게임이면 null
 * @param rerunOfId 다시 돌린 회차면 원 회차 id, 아니면 null
 */
public record InProgressGameResponse(
		Long gameId,
		GameType type,
		GameStatus status,
		Long penaltyAmount,
		Long rerunOfId) {

	/**
	 * 게임으로 응답을 만든다.
	 */
	public static InProgressGameResponse from(GameSession game) {
		Long rerunOfId = game.getRerunOf() == null ? null : game.getRerunOf().getId();
		return new InProgressGameResponse(game.getId(), game.getType(), game.getStatus(), game.getPenaltyAmount(),
				rerunOfId);
	}
}
