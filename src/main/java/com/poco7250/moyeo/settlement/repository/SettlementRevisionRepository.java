package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 정산 리비전 저장소.
 */
public interface SettlementRevisionRepository extends JpaRepository<SettlementRevision, Long> {

	/**
	 * 정산의 특정 번호 리비전을 조회한다.
	 */
	Optional<SettlementRevision> findBySettlementIdAndRevisionNo(Long settlementId, int revisionNo);

	/**
	 * 정산의 모든 리비전을 번호 순으로 조회한다.
	 */
	List<SettlementRevision> findAllBySettlementIdOrderByRevisionNoAsc(Long settlementId);
}
