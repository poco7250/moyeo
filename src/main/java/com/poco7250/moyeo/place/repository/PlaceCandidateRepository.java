package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 후보 저장소.
 */
public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

	/**
	 * 차수의 후보를 등록 순서(id)대로 조회한다.
	 */
	List<PlaceCandidate> findAllByRoundIdOrderById(Long roundId);

	/**
	 * 이 차수에 속한 후보만 조회한다(다른 차수 후보는 없는 것으로 본다).
	 */
	Optional<PlaceCandidate> findByIdAndRoundId(Long id, Long roundId);
}
