package com.poco7250.moyeo.boardgame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 보드게임 기록 요청의 참가자 한 명 순위.
 *
 * @param rank 1 이상의 순위. 같은 순위(공동 순위)를 여러 명에게 줄 수 있다
 */
public record BoardGameRankRequest(@NotNull Long memberId, @NotNull @Min(1) Integer rank) {
}
