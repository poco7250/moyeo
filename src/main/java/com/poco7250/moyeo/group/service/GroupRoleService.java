package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 역할 변경(G-4). 방장만 총무를 지정·해제하고 방장 자리를 넘긴다.
 * 그룹 행을 잠가 역할 변경끼리 한 줄로 세우고, 멤버십은 잠금 읽기로 다시 읽어 먼저 커밋된 양도를 놓치지 않는다
 * (일반 조회는 멤버 검사 때 잡힌 REPEATABLE READ 스냅샷이라 방장이 둘이 될 수 있다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupRoleService {

	private final GroupAccessChecker groupAccessChecker;
	private final GroupRepository groupRepository;
	private final GroupMemberRepository groupMemberRepository;

	/**
	 * 방장이 멤버를 총무로 지정하거나 일반 멤버로 되돌린다. 같은 역할이면 그대로 돌려준다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 방장 아님 NOT_GROUP_OWNER, 대상이 그룹 멤버가 아니면
	 *         GROUP_MEMBER_NOT_FOUND, 요청 역할이 OWNER거나 대상이 방장이면 GROUP_ROLE_INVALID_CHANGE
	 */
	@Transactional
	public GroupMemberResponse changeRole(Long groupId, Long actorId, Long targetId, GroupRole role) {
		lockAsOwner(groupId, actorId);
		if (role == GroupRole.OWNER) {
			throw new BusinessException(ErrorCode.GROUP_ROLE_INVALID_CHANGE);
		}
		GroupMember target = lockTarget(groupId, targetId);
		if (target.isOwner()) {
			throw new BusinessException(ErrorCode.GROUP_ROLE_INVALID_CHANGE);
		}
		target.changeRole(role);
		log.info("그룹 역할 변경: groupId={}, targetId={}, role={}", groupId, targetId, role);
		return GroupMemberResponse.from(target);
	}

	/**
	 * 방장이 방장 자리를 다른 멤버에게 넘긴다. 새 방장은 OWNER, 이전 방장은 MEMBER가 된다.
	 *
	 * @return 바뀐 두 멤버(이전 방장, 새 방장 순)
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 방장 아님 NOT_GROUP_OWNER, 대상이 그룹 멤버가 아니면
	 *         GROUP_MEMBER_NOT_FOUND, 자기 자신에게 넘기면 GROUP_ROLE_INVALID_CHANGE
	 */
	@Transactional
	public List<GroupMemberResponse> transferOwner(Long groupId, Long actorId, Long targetId) {
		GroupMember owner = lockAsOwner(groupId, actorId);
		if (Objects.equals(actorId, targetId)) {
			throw new BusinessException(ErrorCode.GROUP_ROLE_INVALID_CHANGE);
		}
		GroupMember target = lockTarget(groupId, targetId);
		owner.changeRole(GroupRole.MEMBER);
		target.changeRole(GroupRole.OWNER);
		log.info("방장 양도: groupId={}, from={}, to={}", groupId, actorId, targetId);
		return List.of(GroupMemberResponse.from(owner), GroupMemberResponse.from(target));
	}

	/**
	 * 멤버 검사 → 그룹 행 잠금 → 요청자 멤버십 잠금 읽기 → 방장 검사 순으로 진행하고 요청자 멤버십을 돌려준다.
	 */
	private GroupMember lockAsOwner(Long groupId, Long actorId) {
		groupAccessChecker.requireMember(groupId, actorId);
		groupRepository.findByIdForUpdate(groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
		GroupMember actor = groupMemberRepository.findByGroupIdAndMemberIdForUpdate(groupId, actorId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
		if (!actor.isOwner()) {
			throw new BusinessException(ErrorCode.NOT_GROUP_OWNER);
		}
		return actor;
	}

	private GroupMember lockTarget(Long groupId, Long targetId) {
		return groupMemberRepository.findByGroupIdAndMemberIdForUpdate(groupId, targetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
	}
}
