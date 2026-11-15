package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceRoulette;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 동점 룰렛 기록 저장소.
 */
public interface PlaceRouletteRepository extends JpaRepository<PlaceRoulette, Long> {

	/**
	 * 차수의 룰렛 기록을 최근 순으로 대상 후보와 함께 읽는다.
	 */
	@EntityGraph(attributePaths = "candidateIds")
	List<PlaceRoulette> findAllByRoundIdOrderByIdDesc(Long roundId);
}
