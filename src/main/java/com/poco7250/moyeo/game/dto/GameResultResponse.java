package com.poco7250.moyeo.game.dto;

import java.util.List;

/**
 * 공개된 게임 결과.
 *
 * @param seed 모든 화면이 같은 연출을 재생하도록 쓰는 값
 * @param penaltyMemberIds 벌칙자 회원 id(오름차순). 순서 정하기 게임이면 빈 목록
 * @param orderedMemberIds 순서 정하기 게임에서 1번부터 차례로 놓인 회원 id. 그 외 게임이면 null
 */
public record GameResultResponse(Long seed, List<Long> penaltyMemberIds, List<Long> orderedMemberIds) {

	/**
	 * 벌칙자를 뽑는 게임의 결과를 만든다.
	 */
	public static GameResultResponse penalty(Long seed, List<Long> penaltyMemberIds) {
		return new GameResultResponse(seed, penaltyMemberIds, null);
	}

	/**
	 * 순서 정하기 게임의 결과를 만든다.
	 */
	public static GameResultResponse order(Long seed, List<Long> orderedMemberIds) {
		return new GameResultResponse(seed, List.of(), orderedMemberIds);
	}
}
