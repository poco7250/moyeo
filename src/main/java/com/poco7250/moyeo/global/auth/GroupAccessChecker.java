package com.poco7250.moyeo.global.auth;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 그룹 리소스 인가 공통 검사. 그룹에 속한 모든 리소스(모임·정산·게임 등) Service가 진입 시 호출한다.
 */
@Component
@RequiredArgsConstructor
public class GroupAccessChecker {

	private final GroupMemberRepository groupMemberRepository;

	/**
	 * 회원이 그룹 멤버가 아니면 예외를 던진다.
	 * 존재하지 않는 그룹도 같은 403으로 응답해 그룹 존재 여부를 드러내지 않는다.
	 *
	 * @throws BusinessException 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	public void requireMember(Long groupId, Long memberId) {
		if (!groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
			throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
		}
	}

	/**
	 * 회원의 그룹 역할을 조회한다(G-4 권한 판단용). 멤버가 아니면 빈 값이다.
	 */
	public Optional<GroupRole> findRole(Long groupId, Long memberId) {
		return groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId).map(GroupMember::getRole);
	}
}
