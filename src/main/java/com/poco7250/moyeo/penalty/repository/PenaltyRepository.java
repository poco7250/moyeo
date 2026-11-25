package com.poco7250.moyeo.penalty.repository;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.util.Collection;
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
	 * 여러 게임의 무효가 아닌 벌칙을 id 순으로 조회한다(모임 히스토리). 게임·회원은 id만 쓰므로 fetch join하지 않는다.
	 */
	@Query("select p from Penalty p where p.gameSession.id in :gameSessionIds "
			+ "and p.status <> com.poco7250.moyeo.penalty.domain.PenaltyStatus.VOIDED order by p.id")
	List<Penalty> findAllActiveByGameSessionIdIn(@Param("gameSessionIds") Collection<Long> gameSessionIds);

	/**
	 * 모임의 금액형 게임 벌칙 중 무효가 아닌 것을 게임·회원과 함께 게임 순번 → 원 회차 id → 벌칙 id 순으로 조회한다.
	 * 정산 계산과 벌칙 반영 내역(PRD S-4)에 쓴다. 모임 게스트(G-6) 벌칙도 "앱 밖에서 내기" 표시용으로 담고,
	 * 계산 입력을 만들 때 뺀다.
	 */
	@Query("select p from Penalty p join fetch p.gameSession g join fetch p.member "
			+ "where g.meeting.id = :meetingId "
			+ "and p.status <> com.poco7250.moyeo.penalty.domain.PenaltyStatus.VOIDED "
			+ "and g.penaltyKind = com.poco7250.moyeo.game.domain.PenaltyKind.MONEY "
			+ "order by g.applyOrderAt, g.rootSessionId, p.id")
	List<Penalty> findAllActiveMoneyByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 여러 모임의 게임 벌칙 중 무효가 아닌 것을 id 순으로 조회한다(명예의 전당·연말 결산). 회원은 id만 쓰므로 fetch join하지 않는다.
	 */
	@Query("select p from Penalty p where p.gameSession.meeting.id in :meetingIds "
			+ "and p.status <> com.poco7250.moyeo.penalty.domain.PenaltyStatus.VOIDED order by p.id")
	List<Penalty> findAllActiveByMeetingIdIn(@Param("meetingIds") Collection<Long> meetingIds);
}
