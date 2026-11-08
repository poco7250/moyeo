package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Meeting;
import java.time.LocalDateTime;
import java.util.List;
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

	/**
	 * 공유 토큰으로 모임을 찾는다(비로그인 OG 경로).
	 */
	Optional<Meeting> findByShareToken(String shareToken);

	/**
	 * 그룹의 다가오는 모임(시작 시각이 기준 시각 이후, 같으면 포함)을 가까운 순으로 조회한다.
	 */
	List<Meeting> findAllByGroupIdAndStartAtGreaterThanEqualOrderByStartAtAsc(Long groupId, LocalDateTime now);

	/**
	 * 그룹의 지난 모임(시작 시각이 기준 시각 이전)을 최근 순으로 조회한다.
	 */
	List<Meeting> findAllByGroupIdAndStartAtLessThanOrderByStartAtDesc(Long groupId, LocalDateTime now);
}
