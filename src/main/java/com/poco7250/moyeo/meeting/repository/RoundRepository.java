package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Round;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 차수 저장소.
 */
public interface RoundRepository extends JpaRepository<Round, Long> {

	/**
	 * 모임의 차수를 순서(seq)대로 조회한다.
	 */
	List<Round> findAllByMeetingIdOrderBySeq(Long meetingId);
}
