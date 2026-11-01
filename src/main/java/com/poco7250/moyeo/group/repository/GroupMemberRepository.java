package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.GroupMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
