package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 후보 저장소.
 */
public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

	/**
	 * 차수의 후보를 등록 순서(id)대로 조회한다.
	 */
	List<PlaceCandidate> findAllByRoundIdOrderById(Long roundId);
}
