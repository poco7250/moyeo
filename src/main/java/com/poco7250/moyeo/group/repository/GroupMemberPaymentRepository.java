package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.GroupMemberPayment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 그룹 멤버 송금 정보 저장소.
 */
public interface GroupMemberPaymentRepository extends JpaRepository<GroupMemberPayment, Long> {

	/**
	 * 그룹 멤버 1명의 송금 정보를 조회한다.
	 */
	@Query("select p from GroupMemberPayment p where p.groupMember.id = :groupMemberId")
	Optional<GroupMemberPayment> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);

	/**
	 * 그룹 안 여러 회원의 송금 정보를 그룹 멤버와 함께 조회한다(송금 안내 받는 사람용, N+1 방지).
	 */
	@Query("select p from GroupMemberPayment p join fetch p.groupMember gm "
			+ "where gm.group.id = :groupId and gm.member.id in :memberIds")
	List<GroupMemberPayment> findAllByGroupIdAndMemberIdIn(@Param("groupId") Long groupId,
			@Param("memberIds") Collection<Long> memberIds);
}
