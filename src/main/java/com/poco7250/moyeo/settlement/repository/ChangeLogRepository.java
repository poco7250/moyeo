package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.ChangeLog;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * 변경 로그 저장소. append-only라 저장과 조회만 노출한다.
 */
public interface ChangeLogRepository extends Repository<ChangeLog, Long> {

	/**
	 * 변경 로그를 저장한다.
	 */
	ChangeLog save(ChangeLog changeLog);

	/**
	 * 모임의 변경 로그를 오래된 순으로 조회한다.
	 */
	List<ChangeLog> findAllByMeetingIdOrderByChangedAtAscIdAsc(Long meetingId);
}
