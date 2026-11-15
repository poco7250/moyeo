package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameResult;
import java.util.Collection;
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

	/**
	 * 게임에서 주어진 결과를 받은 참가자의 결과를 순번 순으로 조회한다(순서 정하기 게임용).
	 */
	List<GameResult> findAllByGameSessionIdAndOutcomeOrderByOrderNoAsc(Long gameSessionId, String outcome);

	/**
	 * 여러 게임에서 주어진 결과를 받은 참가자의 결과를 한 번에 조회한다.
	 */
	List<GameResult> findAllByGameSessionIdInAndOutcome(Collection<Long> gameSessionIds, String outcome);
}
