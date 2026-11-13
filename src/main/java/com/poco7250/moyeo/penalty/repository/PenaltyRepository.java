package com.poco7250.moyeo.penalty.repository;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 벌칙 저장소.
 */
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

	/**
	 * 게임의 벌칙 중 주어진 상태가 아닌 것만 조회한다(VOIDED를 넘기면 비무효 벌칙).
	 */
	List<Penalty> findAllByGameSessionIdAndStatusNot(Long gameSessionId, PenaltyStatus status);

	/**
	 * 모임의 금액형 게임 벌칙 중 무효가 아닌 것을 게임·차수와 함께 게임 순번 → 원 회차 id → 벌칙 id 순으로 조회한다.
	 * 정산 계산과 벌칙 반영 내역(PRD S-4)에 쓴다.
	 */
	@Query("select p from Penalty p join fetch p.gameSession g join fetch g.round r "
			+ "where r.meeting.id = :meetingId "
			+ "and p.status <> com.poco7250.moyeo.penalty.domain.PenaltyStatus.VOIDED "
			+ "and g.penaltyKind = com.poco7250.moyeo.game.domain.PenaltyKind.MONEY "
			+ "order by g.applyOrderAt, g.rootSessionId, p.id")
	List<Penalty> findAllActiveMoneyByMeetingId(@Param("meetingId") Long meetingId);
}
