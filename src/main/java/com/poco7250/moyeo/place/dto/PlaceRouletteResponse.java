package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.place.domain.PlaceRoulette;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 장소 동점 룰렛 기록. candidateIds는 추첨 순서대로이고 첫 번째가 당첨 후보다. seed로 게임 룰렛과 같은 연출을 재현한다.
 */
public record PlaceRouletteResponse(Long id, Long roundId, long seed, List<Long> candidateIds,
		Long winnerCandidateId, Long spunByMemberId, LocalDateTime spunAt) {

	/**
	 * 룰렛 기록으로 응답을 만든다. 연관 엔티티는 LAZY 프록시의 id만 읽는다.
	 */
	public static PlaceRouletteResponse from(PlaceRoulette roulette) {
		return new PlaceRouletteResponse(roulette.getId(), roulette.getRound().getId(), roulette.getSeed(),
				List.copyOf(roulette.getCandidateIds()), roulette.getWinner().getId(), roulette.getSpunBy().getId(),
				roulette.getSpunAt());
	}
}
