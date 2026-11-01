package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupCreateRequest;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.group.dto.GroupResponse;
import com.poco7250.moyeo.group.dto.InviteLinkResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 생성·조회.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

	private final GroupRepository groupRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final MemberRepository memberRepository;
	private final InviteLinkService inviteLinkService;

	/**
	 * 그룹을 만들고, 생성자를 OWNER 멤버로 넣고, 초대 링크를 발급한다. 셋은 한 트랜잭션이다.
	 * 그룹 닉네임 기본값은 카카오 닉네임이다.
	 *
	 * @throws BusinessException 회원이 없으면 MEMBER_NOT_FOUND
	 */
	@Transactional
	public GroupCreateResponse create(Long memberId, GroupCreateRequest request) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

		Group group = groupRepository.save(Group.create(request.name()));
		groupMemberRepository.save(GroupMember.join(group, member, member.getNickname(), GroupRole.OWNER));
		InviteLinkResponse inviteLink = inviteLinkService.issue(group);
		return new GroupCreateResponse(GroupResponse.from(group), inviteLink);
	}

	/**
	 * 내가 속한 그룹을 최근 가입 순으로 조회한다.
	 */
	@Transactional(readOnly = true)
	public List<GroupResponse> getMyGroups(Long memberId) {
		return groupMemberRepository.findAllWithGroupByMemberId(memberId).stream()
				.map(groupMember -> GroupResponse.from(groupMember.getGroup()))
				.toList();
	}
}
