package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게임 참가자 저장소.
 */
public interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {
}
