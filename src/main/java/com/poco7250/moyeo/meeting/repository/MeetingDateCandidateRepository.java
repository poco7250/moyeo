package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.MeetingDateCandidate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 모임 날짜 후보 저장소.
 */
public interface MeetingDateCandidateRepository extends JpaRepository<MeetingDateCandidate, Long> {

	/**
	 * 모임의 날짜 후보를 이른 시각 순으로 조회한다.
	 */
	List<MeetingDateCandidate> findAllByMeetingIdOrderByStartAtAscIdAsc(Long meetingId);

	/**
	 * 모임에 속한 후보 하나를 조회한다(다른 모임 후보 id 대입 방지).
	 */
	Optional<MeetingDateCandidate> findByIdAndMeetingId(Long id, Long meetingId);

	/**
	 * 같은 시각의 후보가 이미 있는지 확인한다.
	 */
	boolean existsByMeetingIdAndStartAt(Long meetingId, LocalDateTime startAt);

	/**
	 * 모임의 후보 수를 센다.
	 */
	long countByMeetingId(Long meetingId);
}
