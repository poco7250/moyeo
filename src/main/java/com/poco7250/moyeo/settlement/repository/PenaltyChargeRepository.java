package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.dto.MemberPenaltyAmount;
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

	/**
	 * 여러 모임의 마지막 확정 리비전(정산의 현재 리비전 번호)에서 벌칙자별 벌칙 부담액 합을 구한다(명예의 전당).
	 * 한 번도 확정되지 않은 모임은 리비전이 없어 빠진다.
	 */
	@Query("select new com.poco7250.moyeo.settlement.dto.MemberPenaltyAmount(pc.penalty.member.id, sum(pc.amount)) "
			+ "from PenaltyCharge pc join pc.revision r join r.settlement s "
			+ "where s.meeting.id in :meetingIds and r.revisionNo = s.currentRevisionNo "
			+ "group by pc.penalty.member.id")
	List<MemberPenaltyAmount> sumLatestAmountByMemberAndMeetingIdIn(@Param("meetingIds") Collection<Long> meetingIds);
}
