package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;

/**
 * 변경 로그에 남기는 게임 값.
 */
public record GameChangeSnapshot(GameStatus status, GameType type, PenaltyKind penaltyKind)
		implements ChangeLogSnapshot {

	/**
	 * 주어진 상태 기준의 스냅샷을 만든다.
	 */
	public static GameChangeSnapshot of(GameSession game, GameStatus status) {
		return new GameChangeSnapshot(status, game.getType(), game.getPenaltyKind());
	}
}
