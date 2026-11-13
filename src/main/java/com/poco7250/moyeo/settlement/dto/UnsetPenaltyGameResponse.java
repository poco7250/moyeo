package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.penalty.domain.Penalty;
import java.util.List;

/**
 * 정산 확정을 막은 조건 미설정 금액형 게임 1건(PRD S-10). 조건 입력·면제가 게임 단위라 게임별로 묶는다.
 *
 * @param penaltyIds 이 게임의 미설정 벌칙 id(오름차순)
 * @param memberIds  벌칙자 회원 id(penaltyIds와 같은 순서)
 */
public record UnsetPenaltyGameResponse(
		Long gameId,
		GameType type,
		int roundSeq,
		String roundName,
		List<Long> penaltyIds,
		List<Long> memberIds) {

	/**
	 * 같은 게임의 미설정 벌칙들로 응답을 만든다.
	 *
	 * @param penalties 같은 게임의 벌칙(비어 있으면 안 됨)
	 */
	public static UnsetPenaltyGameResponse of(List<Penalty> penalties) {
		GameSession game = penalties.getFirst().getGameSession();
		return new UnsetPenaltyGameResponse(game.getId(), game.getType(), game.getRound().getSeq(),
				game.getRound().getName(),
				penalties.stream().map(Penalty::getId).toList(),
				penalties.stream().map(penalty -> penalty.getMember().getId()).toList());
	}
}
