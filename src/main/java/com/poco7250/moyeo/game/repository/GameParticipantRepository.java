package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게임 참가자 저장소.
 */
public interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {

	/**
	 * 게임의 참가자를 등록 순으로 조회한다.
	 */
	List<GameParticipant> findAllByGameSessionIdOrderByIdAsc(Long gameSessionId);
}
