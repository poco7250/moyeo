package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Round;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 차수 저장소.
 */
public interface RoundRepository extends JpaRepository<Round, Long> {

	/**
	 * 모임의 차수를 순서(seq)대로 조회한다.
	 */
	List<Round> findAllByMeetingIdOrderBySeq(Long meetingId);

	/**
	 * 마감이 지났고 아직 마감 처리하지 않은 투표 차수의 id를 조회한다. 마감 정각은 아직 열려 있어 제외한다.
	 */
	@Query("select r.id from Round r where r.placeStatus = com.poco7250.moyeo.meeting.domain.PlaceStatus.VOTING "
			+ "and r.placeVoteDeadline < :now and r.placeVoteClosedAt is null")
	List<Long> findVoteClosableIds(@Param("now") LocalDateTime now);
}
