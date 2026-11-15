package com.poco7250.moyeo.history.dto;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 히스토리의 게임 한 건. 다시 돌린 게임은 마지막으로 공개된 회차만 나온다.
 *
 * @param gameId 그 회차의 게임 id
 * @param penalized 벌칙자(무효 벌칙 제외)
 */
public record GameHistoryResponse(Long gameId, GameType type, PenaltyKind penaltyKind, String penaltyNote,
		LocalDateTime revealedAt, List<HistoryMemberResponse> penalized) {
}
