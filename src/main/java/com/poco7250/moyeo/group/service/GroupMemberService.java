package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.dto.GroupMemberUpdateRequest;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 멤버 목록과 그룹 안 내 프로필(G-2).
 */
@Service
@RequiredArgsConstructor
public class GroupMemberService {

	private final GroupMemberRepository groupMemberRepository;
	private final GroupAccessChecker groupAccessChecker;

	/**
	 * 그룹 멤버를 가입 순으로 조회한다. 그룹 멤버만 볼 수 있다.
	 *
	 * @throws BusinessException 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<GroupMemberResponse> getMembers(Long groupId, Long memberId) {
		groupAccessChecker.requireMember(groupId, memberId);
		return groupMemberRepository.findAllByGroupIdOrderByIdAsc(groupId).stream()
				.map(GroupMemberResponse::from)
				.toList();
	}

	/**
	 * 그룹 안 내 닉네임·이모지를 바꾼다.
	 *
	 * @throws BusinessException 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional
	public GroupMemberResponse updateMyProfile(Long groupId, Long memberId, GroupMemberUpdateRequest request) {
		GroupMember groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
		groupMember.updateProfile(request.nickname(), request.emoji());
		return GroupMemberResponse.from(groupMember);
	}
}
