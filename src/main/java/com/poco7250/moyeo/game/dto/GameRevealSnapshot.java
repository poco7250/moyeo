package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import java.util.List;

/**
 * 변경 로그에 남기는 결과 공개 후의 게임 값. 누가 벌칙자로 확정됐는지 함께 남긴다.
 *
 * @param penaltyMemberIds 벌칙자 회원 id(오름차순)
 */
public record GameRevealSnapshot(GameStatus status, GameType type, PenaltyKind penaltyKind,
		List<Long> penaltyMemberIds) implements ChangeLogSnapshot {

	/**
	 * 게임의 현재 상태와 확정된 벌칙자로 스냅샷을 만든다.
	 */
	public static GameRevealSnapshot of(GameSession game, List<Long> penaltyMemberIds) {
		return new GameRevealSnapshot(game.getStatus(), game.getType(), game.getPenaltyKind(),
				List.copyOf(penaltyMemberIds));
	}
}
