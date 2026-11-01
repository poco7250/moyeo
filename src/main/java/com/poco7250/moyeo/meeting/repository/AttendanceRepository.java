package com.poco7250.moyeo.meeting.repository;

import com.poco7250.moyeo.meeting.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 참석 응답 저장소. 응답·현황판 쿼리는 Task 013에서 추가한다.
 */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
}
