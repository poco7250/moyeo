package com.poco7250.moyeo.history.dto;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 히스토리의 게임 한 건. 다시 돌린 게임은 마지막으로 공개된 회차만 나온다.
 *
 * @param gameId 그 회차의 게임 id
 * @param penaltyAmount 금액형 벌칙자 1인 금액(원). 금액형이 아니거나 금액 없는 옛 게임이면 null
 * @param penalized 벌칙자(무효 벌칙 제외)
 * @param ordered 순서 정하기 게임에서 1번부터 차례로 놓인 참가자. 그 외 게임이면 빈 목록
 */
public record GameHistoryResponse(Long gameId, GameType type, PenaltyKind penaltyKind, Long penaltyAmount,
		String penaltyNote,
		LocalDateTime revealedAt, List<HistoryMemberResponse> penalized, List<HistoryMemberResponse> ordered) {
}
