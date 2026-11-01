package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.GroupMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 그룹 멤버 저장소.
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

	/**
	 * 회원이 그룹 멤버인지 확인한다(그룹 리소스 인가용).
	 */
	boolean existsByGroupIdAndMemberId(Long groupId, Long memberId);

	/**
	 * 그룹의 멤버를 모두 조회한다.
	 */
	List<GroupMember> findAllByGroupId(Long groupId);

	/**
	 * 회원이 가입한 그룹 멤버십을 그룹과 함께 최근 가입 순으로 조회한다(내 그룹 목록, N+1 방지).
	 */
	@Query("select gm from GroupMember gm join fetch gm.group where gm.member.id = :memberId order by gm.id desc")
	List<GroupMember> findAllWithGroupByMemberId(@Param("memberId") Long memberId);
}
