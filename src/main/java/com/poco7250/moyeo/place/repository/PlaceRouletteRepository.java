package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceRoulette;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장소 동점 룰렛 기록 저장소.
 */
public interface PlaceRouletteRepository extends JpaRepository<PlaceRoulette, Long> {

	/**
	 * 차수의 룰렛 기록을 최근 순으로 대상 후보와 함께 읽는다.
	 */
	@EntityGraph(attributePaths = "candidateIds")
	List<PlaceRoulette> findAllByRoundIdOrderByIdDesc(Long roundId);

	/**
	 * 후보가 룰렛 기록(대상 후보 또는 당첨)에 들어 있는지 본다. 들어 있으면 기록을 지키려고 후보를 지우지 않는다.
	 */
	@Query("select count(r) > 0 from PlaceRoulette r "
			+ "where r.winner.id = :candidateId or :candidateId member of r.candidateIds")
	boolean existsByCandidateId(@Param("candidateId") Long candidateId);
}
