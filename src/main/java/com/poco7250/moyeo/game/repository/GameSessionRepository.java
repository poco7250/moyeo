package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameSession;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게임 저장소.
 */
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

	/**
	 * 게임이 속한 그룹 ID만 조회한다(게임 토픽 구독 인가용, 엔티티를 읽지 않음).
	 */
	@Query("select g.meeting.group.id from GameSession g where g.id = :gameId")
	Optional<Long> findGroupIdById(@Param("gameId") Long gameId);

	/**
	 * 모임의 게임을 최근에 만든 순으로 조회한다(취소·종료된 게임 포함).
	 */
	List<GameSession> findAllByMeetingIdOrderByIdDesc(Long meetingId);

	/**
	 * 주어진 회차를 다시 돌린 후속 회차가 있는지 확인한다(취소된 후속 회차는 rerunOf를 비우므로 제외된다).
	 */
	boolean existsByRerunOfId(Long rerunOfId);

	/**
	 * 모임의 금액형 게임 중 대기·진행 중인 것(다시 돌린 회차 포함)을 id 순으로 조회한다(정산 확정 게이트용).
	 */
	@Query("select g from GameSession g "
			+ "where g.meeting.id = :meetingId "
			+ "and g.penaltyKind = com.poco7250.moyeo.game.domain.PenaltyKind.MONEY "
			+ "and g.status in (com.poco7250.moyeo.game.domain.GameStatus.WAITING, "
			+ "com.poco7250.moyeo.game.domain.GameStatus.PLAYING) "
			+ "order by g.id")
	List<GameSession> findAllMoneyInProgressByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 여러 모임의 결과 공개된 게임(REVEALED·CLOSED, 다시 돌린 회차 포함)을 id 순으로 조회한다(모임 히스토리).
	 */
	@Query("select g from GameSession g where g.meeting.id in :meetingIds "
			+ "and g.status in (com.poco7250.moyeo.game.domain.GameStatus.REVEALED, "
			+ "com.poco7250.moyeo.game.domain.GameStatus.CLOSED) order by g.id")
	List<GameSession> findAllRevealedByMeetingIdIn(@Param("meetingIds") Collection<Long> meetingIds);
}
