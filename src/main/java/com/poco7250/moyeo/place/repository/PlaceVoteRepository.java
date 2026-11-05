package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장소 후보 투표 저장소.
 */
public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

	/**
	 * 회원이 이 후보에 이미 투표했는지 본다.
	 */
	boolean existsByCandidateIdAndMemberId(Long candidateId, Long memberId);

	/**
	 * 차수의 모든 후보에 달린 표를 조회한다. 후보·회원은 LAZY 프록시의 id만 읽으면 추가 쿼리가 나가지 않는다.
	 */
	@Query("select v from PlaceVote v where v.candidate.round.id = :roundId")
	List<PlaceVote> findAllByRoundId(@Param("roundId") Long roundId);

	/**
	 * 회원의 이 후보 표를 지운다.
	 *
	 * @return 지운 표 수(표가 없었으면 0)
	 */
	@Modifying
	@Query("delete from PlaceVote v where v.candidate.id = :candidateId and v.member.id = :memberId")
	int deleteByCandidateIdAndMemberId(@Param("candidateId") Long candidateId, @Param("memberId") Long memberId);

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
