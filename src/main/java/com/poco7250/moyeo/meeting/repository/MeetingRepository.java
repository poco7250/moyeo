package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Meeting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모임 저장소.
 */
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

	/**
	 * 모임이 속한 그룹 ID만 조회한다(모임 토픽 구독 인가용, 엔티티를 읽지 않음).
	 */
	@Query("select m.group.id from Meeting m where m.id = :meetingId")
	Optional<Long> findGroupIdById(@Param("meetingId") Long meetingId);
}
