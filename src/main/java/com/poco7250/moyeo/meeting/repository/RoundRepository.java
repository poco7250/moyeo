package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.place.dto.ConfirmedPlaceRow;
import java.time.LocalDateTime;
import java.util.Collection;
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
	 * 모임의 차수를 확정 장소 후보와 함께 순서(seq)대로 조회한다(모임 상세·OG, N+1 방지). 미확정 차수도 포함한다.
	 */
	@Query("select r from Round r left join fetch r.confirmedCandidate "
			+ "where r.meeting.id = :meetingId order by r.seq")
	List<Round> findAllWithConfirmedPlaceByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 여러 모임의 차수를 확정 장소 후보와 함께 모임 → 순서(seq)대로 조회한다(모임 히스토리, N+1 방지).
	 */
	@Query("select r from Round r left join fetch r.confirmedCandidate "
			+ "where r.meeting.id in :meetingIds order by r.meeting.id, r.seq")
	List<Round> findAllWithConfirmedPlaceByMeetingIdIn(@Param("meetingIds") Collection<Long> meetingIds);

	/**
	 * 마감이 지났고 아직 마감 처리하지 않은 투표 차수의 id를 조회한다. 마감 정각은 아직 열려 있어 제외한다.
	 */
	@Query("select r.id from Round r where r.placeStatus = com.poco7250.moyeo.meeting.domain.PlaceStatus.VOTING "
			+ "and r.placeVoteDeadline < :now and r.placeVoteClosedAt is null")
	List<Long> findVoteClosableIds(@Param("now") LocalDateTime now);

	/**
	 * 여러 모임의 차수를 조회한다(명예의 전당). 장소는 쓰지 않아 fetch join하지 않는다.
	 */
	List<Round> findAllByMeetingIdIn(Collection<Long> meetingIds);

	/**
	 * 그룹의 지난 모임(시작 시각이 기준 시각 이전)에서 장소가 확정된 차수의 장소를 최근 모임 → 차수 순으로 조회한다
	 * (지난 장소 불러오기, P-8). 날짜 투표 중인 모임은 시작 시각이 없어 빠진다.
	 */
	@Query("select new com.poco7250.moyeo.place.dto.ConfirmedPlaceRow(c.place, m.startAt) "
			+ "from Round r join r.confirmedCandidate c join r.meeting m "
			+ "where m.group.id = :groupId and m.startAt < :now "
			+ "and r.placeStatus = com.poco7250.moyeo.meeting.domain.PlaceStatus.CONFIRMED "
			+ "order by m.startAt desc, m.id desc, r.seq asc")
	List<ConfirmedPlaceRow> findAllConfirmedPlacesOfPastMeetings(@Param("groupId") Long groupId,
			@Param("now") LocalDateTime now);
}
