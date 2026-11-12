package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import java.util.List;

/**
 * 변경 로그에 남기는 다시 돌린 회차의 결과 공개 값. 새 벌칙자와 함께 무효가 된 원 회차 벌칙자를 남긴다.
 *
 * @param penaltyMemberIds 새 벌칙자 회원 id(오름차순)
 * @param rerunOfId 다시 돌린 대상인 원 회차 id
 * @param voidedMemberIds 벌칙이 무효(VOIDED)가 된 원 회차 벌칙자 회원 id(오름차순)
 * @param inheritedStatus 새 벌칙자가 원 회차에서 이어받은 조건 상태(ASSIGNED·APPLIED·WAIVED)
 */
public record GameRerunRevealSnapshot(GameStatus status, GameType type, PenaltyKind penaltyKind,
		List<Long> penaltyMemberIds, Long rerunOfId, List<Long> voidedMemberIds, String inheritedStatus)
		implements ChangeLogSnapshot {

	/**
	 * 공개된 다시 돌린 회차와 무효 처리 결과로 스냅샷을 만든다.
	 */
	public static GameRerunRevealSnapshot of(GameSession game, List<Long> penaltyMemberIds, Long rerunOfId,
			List<Long> voidedMemberIds, String inheritedStatus) {
		return new GameRerunRevealSnapshot(game.getStatus(), game.getType(), game.getPenaltyKind(),
				List.copyOf(penaltyMemberIds), rerunOfId, List.copyOf(voidedMemberIds), inheritedStatus);
	}
}
