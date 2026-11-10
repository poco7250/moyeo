package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게임 결과 저장소.
 */
public interface GameResultRepository extends JpaRepository<GameResult, Long> {

	/**
	 * 게임에서 주어진 결과를 받은 참가자의 결과를 조회한다.
	 */
	List<GameResult> findAllByGameSessionIdAndOutcome(Long gameSessionId, String outcome);
}
