package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.VoteTally;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 차수의 후보·표를 읽어 VoteTally로 판정한다. 투표 마감 처리와 동점 룰렛이 같은 기준을 쓰도록 한 곳에 둔다.
 * 호출하는 쪽 트랜잭션 안에서 쓴다.
 */
@Component
@RequiredArgsConstructor
class RoundVoteDecider {

	private final PlaceCandidateRepository placeCandidateRepository;
	private final PlaceVoteRepository placeVoteRepository;

	/**
	 * 차수의 현재 득표로 단독 최다 후보 또는 동점 후보를 판정한다.
	 */
	Result decide(Long roundId) {
		List<PlaceCandidate> candidates = placeCandidateRepository.findAllByRoundIdOrderById(roundId);
		VoteTally.Decision decision = VoteTally.decide(
				candidates.stream().map(PlaceCandidate::getId).toList(), countVotes(roundId));
		return new Result(candidates, decision);
	}

	private Map<Long, Long> countVotes(Long roundId) {
		return placeVoteRepository.findAllByRoundId(roundId).stream()
				.collect(Collectors.groupingBy(vote -> vote.getCandidate().getId(), Collectors.counting()));
	}

	/**
	 * 판정 결과와 판정에 쓴 후보 엔티티.
	 */
	record Result(List<PlaceCandidate> candidates, VoteTally.Decision decision) {

		/**
		 * 단독 최다 후보. 없으면 예외다(hasWinner로 먼저 확인).
		 */
		PlaceCandidate winner() {
			return candidates.stream()
					.filter(candidate -> candidate.getId().equals(decision.winnerId()))
					.findFirst()
					.orElseThrow();
		}

		/**
		 * 호스트가 골라야 할 동점 후보(전원 0표면 후보 전체). 등록 순을 유지한다.
		 */
		List<PlaceCandidate> tiedCandidates() {
			Set<Long> tiedIds = Set.copyOf(decision.tiedCandidateIds());
			return candidates.stream().filter(candidate -> tiedIds.contains(candidate.getId())).toList();
		}
	}
}
