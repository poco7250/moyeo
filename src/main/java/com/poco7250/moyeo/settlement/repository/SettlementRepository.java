package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.Settlement;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 정산 저장소.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

	/**
	 * 모임의 정산을 잠금 없이 조회한다.
	 */
	Optional<Settlement> findByMeetingId(Long meetingId);

	/**
	 * 모임의 정산을 커밋 시 version이 강제 증가하도록 조회한다. 같은 모임의 금액 영향 쓰기를 직렬화하는 데 쓴다.
	 */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	@Query("select s from Settlement s where s.meeting.id = :meetingId")
	Optional<Settlement> findByMeetingIdForUpdate(@Param("meetingId") Long meetingId);
}
