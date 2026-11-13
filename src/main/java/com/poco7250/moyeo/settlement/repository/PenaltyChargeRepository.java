package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리비전별 벌칙 부담액 스냅샷 저장소.
 */
public interface PenaltyChargeRepository extends JpaRepository<PenaltyCharge, Long> {

	/**
	 * 리비전의 벌칙 부담액 스냅샷을 저장 순서대로 조회한다.
	 */
	List<PenaltyCharge> findAllByRevisionIdOrderByIdAsc(Long revisionId);

	/**
	 * 여러 리비전의 벌칙 부담액 스냅샷을 한 번에 저장 순서대로 조회한다. 벌칙자 id를 쓰므로 벌칙을 함께 읽는다.
	 */
	@Query("select pc from PenaltyCharge pc join fetch pc.penalty where pc.revision.id in :revisionIds order by pc.id")
	List<PenaltyCharge> findAllWithPenaltyByRevisionIdIn(@Param("revisionIds") Collection<Long> revisionIds);
}
