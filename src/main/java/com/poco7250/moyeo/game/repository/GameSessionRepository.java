package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게임 저장소.
 */
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

	/**
	 * 게임 행을 쓰기 잠금으로 조회한다. 꽝뽑기 카드 선택과 자동 배정을 직렬화하는 데 쓴다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from GameSession g where g.id = :gameId")
	Optional<GameSession> findByIdForPick(@Param("gameId") Long gameId);

	/**
	 * 게임이 속한 그룹 ID만 조회한다(게임 토픽 구독 인가용, 엔티티를 읽지 않음).
	 */
	@Query("select g.round.meeting.group.id from GameSession g where g.id = :gameId")
	Optional<Long> findGroupIdById(@Param("gameId") Long gameId);
}
