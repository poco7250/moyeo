package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.PlaceCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 후보 저장소.
 */
public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {
}
