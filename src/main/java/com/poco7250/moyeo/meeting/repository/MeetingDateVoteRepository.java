package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.MeetingDateVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 날짜 후보 응답 저장소.
 */
public interface MeetingDateVoteRepository extends JpaRepository<MeetingDateVote, Long> {

	/**
	 * 모임의 모든 후보 응답을 조회한다(득표 현황 집계용).
	 */
	@Query("select v from MeetingDateVote v where v.candidate.meeting.id = :meetingId")
	List<MeetingDateVote> findAllByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 한 멤버가 모임의 후보들에 남긴 응답을 조회한다(내 응답 교체용).
	 */
	@Query("select v from MeetingDateVote v where v.candidate.meeting.id = :meetingId and v.member.id = :memberId")
	List<MeetingDateVote> findAllByMeetingIdAndMemberId(@Param("meetingId") Long meetingId,
			@Param("memberId") Long memberId);
}
