package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Attendance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 참석 응답 저장소.
 */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

	/**
	 * 모임의 모든 차수 응답을 조회한다(현황판). 차수·회원은 id만 쓰므로 fetch join하지 않는다.
	 */
	@Query("select a from Attendance a where a.round.meeting.id = :meetingId")
	List<Attendance> findAllByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 모임에서 한 회원의 차수별 응답을 조회한다(응답 upsert 대상).
	 */
	@Query("select a from Attendance a where a.round.meeting.id = :meetingId and a.member.id = :memberId")
	List<Attendance> findAllByMeetingIdAndMemberId(@Param("meetingId") Long meetingId,
			@Param("memberId") Long memberId);
}
