package com.poco7250.moyeo.place.domain;

import java.util.List;
import java.util.Map;

/**
 * 후보별 득표로 투표 결과를 판정하는 순수 함수(PRD P-4).
 */
public final class VoteTally {

	private VoteTally() {
	}

	/**
	 * 단독 최다 후보가 있으면 winner로, 없으면 호스트가 골라야 할 후보들을 돌려준다.
	 * 전원 0표는 아무도 고르지 않은 것이라 후보가 1개여도 자동 확정하지 않는다. 후보가 없으면 고를 목록도 비어 있다.
	 *
	 * @param candidateIds 차수의 전체 후보 id(순서 유지)
	 * @param voteCounts 후보 id별 득표 수(표가 없는 후보는 빠져 있어도 된다)
	 */
	public static Decision decide(List<Long> candidateIds, Map<Long, Long> voteCounts) {
		long maxVotes = candidateIds.stream()
				.mapToLong(id -> voteCounts.getOrDefault(id, 0L))
				.max()
				.orElse(0L);
		if (maxVotes == 0L) {
			return new Decision(null, candidateIds);
		}
		List<Long> topCandidateIds = candidateIds.stream()
				.filter(id -> voteCounts.getOrDefault(id, 0L) == maxVotes)
				.toList();
		return topCandidateIds.size() == 1
				? new Decision(topCandidateIds.get(0), List.of())
				: new Decision(null, topCandidateIds);
	}

	/**
	 * 판정 결과. winnerId가 있으면 자동 확정, 없으면 tiedCandidateIds 중에서 호스트가 고른다.
	 */
	public record Decision(Long winnerId, List<Long> tiedCandidateIds) {

		/**
		 * 단독 최다 후보가 있는지 본다.
		 */
		public boolean hasWinner() {
			return winnerId != null;
		}
	}
}
