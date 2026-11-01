package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.GroupMember;
import java.util.List;
import java.util.Optional;
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
	 * 그룹의 특정 회원 멤버십을 조회한다(초대 재진입 판단, 내 그룹 프로필 수정).
	 */
	Optional<GroupMember> findByGroupIdAndMemberId(Long groupId, Long memberId);

	/**
	 * 그룹의 멤버를 가입 순으로 모두 조회한다(멤버 목록). 회원은 id만 쓰므로 fetch join하지 않는다.
	 */
	List<GroupMember> findAllByGroupIdOrderByIdAsc(Long groupId);

	/**
	 * 그룹의 멤버 수를 센다(초대 미리보기).
	 */
	long countByGroupId(Long groupId);

	/**
	 * 회원이 가입한 그룹 멤버십을 그룹과 함께 최근 가입 순으로 조회한다(내 그룹 목록, N+1 방지).
	 */
	@Query("select gm from GroupMember gm join fetch gm.group where gm.member.id = :memberId order by gm.id desc")
	List<GroupMember> findAllWithGroupByMemberId(@Param("memberId") Long memberId);
}
