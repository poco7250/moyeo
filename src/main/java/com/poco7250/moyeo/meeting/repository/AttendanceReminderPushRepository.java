package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.AttendanceReminderPush;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리마인드 푸시 기록 저장소.
 */
public interface AttendanceReminderPushRepository extends JpaRepository<AttendanceReminderPush, Long> {

	/**
	 * 모임의 리마인드 푸시 기록을 잠금 읽기로 조회한다. 일반 조회는 트랜잭션 앞부분에서 잡힌 REPEATABLE READ 스냅샷이라
	 * 먼저 커밋된 기록을 못 볼 수 있다. 모임 행 잠금 뒤에 불러 잠금 순서가 늘 같다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from AttendanceReminderPush p where p.meeting.id = :meetingId")
	Optional<AttendanceReminderPush> findByMeetingIdForUpdate(@Param("meetingId") Long meetingId);
}
