package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게임 결과 저장소.
 */
public interface GameResultRepository extends JpaRepository<GameResult, Long> {
}
