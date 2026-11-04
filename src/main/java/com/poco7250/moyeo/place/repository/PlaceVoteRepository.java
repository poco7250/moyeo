package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 후보 투표 저장소.
 */
public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {
}
