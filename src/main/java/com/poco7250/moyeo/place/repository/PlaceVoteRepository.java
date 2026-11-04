package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장소 후보 투표 저장소.
 */
public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

	/**
	 * 차수의 모든 후보에 달린 표를 한 번에 지운다(재투표 시 표 초기화).
	 * 벌크 delete는 경로 join을 쓸 수 없어 후보 id 서브쿼리로 거른다.
	 *
	 * @return 지운 표 수
	 */
	@Modifying
	@Query("delete from PlaceVote v where v.candidate.id in "
			+ "(select c.id from PlaceCandidate c where c.round.id = :roundId)")
	int deleteAllByRoundId(@Param("roundId") Long roundId);
}
