package com.poco7250.moyeo.penalty.repository;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 벌칙 저장소.
 */
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

	/**
	 * 게임의 벌칙 중 주어진 상태가 아닌 것만 조회한다(VOIDED를 넘기면 비무효 벌칙).
	 */
	List<Penalty> findAllByGameSessionIdAndStatusNot(Long gameSessionId, PenaltyStatus status);
}
