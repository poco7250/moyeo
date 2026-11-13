package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 리비전별 벌칙 부담액 스냅샷 저장소.
 */
public interface PenaltyChargeRepository extends JpaRepository<PenaltyCharge, Long> {

	/**
	 * 리비전의 벌칙 부담액 스냅샷을 저장 순서대로 조회한다.
	 */
	List<PenaltyCharge> findAllByRevisionIdOrderByIdAsc(Long revisionId);
}
