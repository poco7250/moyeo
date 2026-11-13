package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.Transfer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 송금 안내 저장소.
 */
public interface TransferRepository extends JpaRepository<Transfer, Long> {

	/**
	 * 리비전의 송금 안내를 계산된 순서대로 조회한다.
	 */
	List<Transfer> findAllByRevisionIdOrderByIdAsc(Long revisionId);

	/**
	 * 여러 리비전의 송금 안내를 한 번에 저장 순서대로 조회한다(리비전 목록 조회용).
	 */
	List<Transfer> findAllByRevisionIdInOrderByIdAsc(Collection<Long> revisionIds);
}
