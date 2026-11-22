package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import java.util.Collection;
import java.util.List;
import com.poco7250.moyeo.settlement.dto.MemberShareAmount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * 여러 모임의 마지막 확정 리비전(정산의 현재 리비전 번호)에서 회원별 부담액 합을 구한다(연말 결산).
	 * 한 번도 확정되지 않은 모임은 리비전이 없어 빠진다.
	 */
	@Query("select new com.poco7250.moyeo.settlement.dto.MemberShareAmount(rs.member.id, sum(rs.shareAmount)) "
			+ "from RevisionMemberShare rs join rs.revision r join r.settlement s "
			+ "where s.meeting.id in :meetingIds and r.revisionNo = s.currentRevisionNo "
			+ "group by rs.member.id")
	List<MemberShareAmount> sumLatestShareByMemberAndMeetingIdIn(@Param("meetingIds") Collection<Long> meetingIds);
}
