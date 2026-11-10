package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameSession;
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
	@Query("select g.round.meeting.group.id from GameSession g where g.id = :gameId")
	Optional<Long> findGroupIdById(@Param("gameId") Long gameId);
}
