package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.MeetingGuest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모임 게스트 저장소(PRD G-6).
 */
public interface MeetingGuestRepository extends JpaRepository<MeetingGuest, Long> {

	/**
	 * 모임의 게스트를 추가 순으로 게스트 회원과 함께 조회한다(모임 상세·실시간 목록).
	 */
	@Query("select g from MeetingGuest g join fetch g.member where g.meeting.id = :meetingId order by g.id")
	List<MeetingGuest> findAllWithMemberByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 여러 모임의 게스트를 게스트 회원과 함께 조회한다(히스토리·결산 카드의 이름 표시).
	 */
	@Query("select g from MeetingGuest g join fetch g.member where g.meeting.id in :meetingIds")
	List<MeetingGuest> findAllWithMemberByMeetingIdIn(@Param("meetingIds") Collection<Long> meetingIds);

	/**
	 * 모임의 게스트 하나를 게스트 회원 id로 찾는다.
	 */
	Optional<MeetingGuest> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

	/**
	 * 모임의 게스트 중 주어진 회원 id에 해당하는 게스트 회원 id만 돌려준다(게임 참가자 검사).
	 */
	@Query("select g.member.id from MeetingGuest g where g.meeting.id = :meetingId and g.member.id in :memberIds")
	List<Long> findMemberIdsByMeetingIdAndMemberIdIn(@Param("meetingId") Long meetingId,
			@Param("memberIds") Collection<Long> memberIds);
}
