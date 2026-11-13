package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 리비전 회원별 스냅샷 저장소.
 */
public interface RevisionMemberShareRepository extends JpaRepository<RevisionMemberShare, Long> {

	/**
	 * 리비전의 회원별 스냅샷을 회원 id 순으로 조회한다. 회원은 id만 쓰므로 fetch join하지 않는다.
	 */
	List<RevisionMemberShare> findAllByRevisionIdOrderByMemberIdAsc(Long revisionId);

	/**
	 * 여러 리비전의 회원별 스냅샷을 한 번에 회원 id 순으로 조회한다(리비전 목록 조회용).
	 */
	List<RevisionMemberShare> findAllByRevisionIdInOrderByMemberIdAsc(Collection<Long> revisionIds);
}
