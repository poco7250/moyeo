package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingDateStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
	 * 그룹의 날짜 상태별 모임을 최근 생성 순으로 조회한다(날짜 투표 중인 모임 목록용).
	 */
	List<Meeting> findAllByGroupIdAndDateStatusOrderByCreatedAtDescIdDesc(Long groupId, MeetingDateStatus dateStatus);

	/**
	 * 그룹의 다가오는 모임(시작 시각이 기준 시각 이후, 같으면 포함)을 가까운 순으로 조회한다.
	 */
	List<Meeting> findAllByGroupIdAndStartAtGreaterThanEqualOrderByStartAtAsc(Long groupId, LocalDateTime now);

	/**
	 * 그룹의 지난 모임(시작 시각이 기준 시각 이전)을 최근 순으로 조회한다.
	 */
	List<Meeting> findAllByGroupIdAndStartAtLessThanOrderByStartAtDesc(Long groupId, LocalDateTime now);

	/**
	 * 그룹의 지난 모임(시작 시각이 기준 시각 이전) 한 페이지를 조회한다(모임 히스토리). 정렬은 Pageable로 준다.
	 */
	Slice<Meeting> findAllByGroupIdAndStartAtLessThan(Long groupId, LocalDateTime now, Pageable pageable);

	/**
	 * 그룹의 모임 중 시작 시각이 [from, to) 안에 있는 것을 최근 순(같은 시각이면 나중에 만든 모임 먼저)으로 조회한다
	 * (명예의 전당). 날짜 투표 중인 모임은 시작 시각이 없어 빠진다.
	 */
	@Query("select m from Meeting m where m.group.id = :groupId and m.startAt >= :from and m.startAt < :to "
			+ "order by m.startAt desc, m.id desc")
	List<Meeting> findAllByGroupIdAndStartAtInRange(@Param("groupId") Long groupId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	/**
	 * 모임을 쓰기 잠금으로 읽는다(리마인드 푸시 쿨다운을 모임 단위로 줄 세움). version은 올리지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Meeting m where m.id = :meetingId")
	Optional<Meeting> findByIdForUpdate(@Param("meetingId") Long meetingId);
}
