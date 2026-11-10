package com.poco7250.moyeo.game.dto;

import java.util.List;

/**
 * 공개된 게임 결과.
 *
 * @param seed 모든 화면이 같은 연출을 재생하도록 쓰는 값
 * @param penaltyMemberIds 벌칙자 회원 id(오름차순)
 */
public record GameResultResponse(Long seed, List<Long> penaltyMemberIds) {
}
