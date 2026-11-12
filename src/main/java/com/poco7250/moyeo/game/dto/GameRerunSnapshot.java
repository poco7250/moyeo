package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;

/**
 * 변경 로그에 남기는 다시 돌린 회차의 값. 어느 회차를 다시 돌렸는지 함께 남긴다.
 *
 * @param rerunOfId 다시 돌린 대상인 원 회차 id
 */
public record GameRerunSnapshot(GameStatus status, GameType type, PenaltyKind penaltyKind, Long rerunOfId)
		implements ChangeLogSnapshot {

	/**
	 * 다시 돌린 회차와 원 회차 id로 스냅샷을 만든다.
	 */
	public static GameRerunSnapshot of(GameSession rerun, Long rerunOfId) {
		return new GameRerunSnapshot(rerun.getStatus(), rerun.getType(), rerun.getPenaltyKind(), rerunOfId);
	}
}
