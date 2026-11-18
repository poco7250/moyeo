package com.poco7250.moyeo.history.dto;

import java.util.List;

/**
 * 히스토리의 보드게임 기록 한 건.
 *
 * @param ranks 순위 오름차순(같은 순위면 회원 id 순)
 * @param loser 꼴등(전액 부담자). 꼴등 지정 도입 전 기록은 null
 */
public record BoardGameHistoryResponse(Long recordId, String gameName, List<BoardGameRankHistoryResponse> ranks,
		HistoryMemberResponse loser) {
}
