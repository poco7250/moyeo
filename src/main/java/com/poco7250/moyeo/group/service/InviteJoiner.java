package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크 가입 한 번을 한 트랜잭션으로 처리한다.
 * 유니크 위반이 나면 이 트랜잭션은 롤백되고, 재시도는 InviteJoinService가 새 트랜잭션으로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteJoiner {

	private final InviteLinkService inviteLinkService;
	private final GroupMemberRepository groupMemberRepository;
	private final MemberRepository memberRepository;

	/**
	 * 링크를 검증하고, 이미 멤버면 그대로, 아니면 MEMBER로 가입시킨다. 그룹 닉네임 기본값은 카카오 닉네임이다.
	 *
	 * @throws BusinessException 링크가 없으면 INVITE_LINK_NOT_FOUND, 만료·폐기면 INVITE_LINK_EXPIRED,
	 *                           회원이 없으면 MEMBER_NOT_FOUND
	 */
	@Transactional
	public InviteJoinResponse join(String token, Long memberId) {
		Group group = inviteLinkService.getUsableLink(token).getGroup();
		if (groupMemberRepository.findByGroupIdAndMemberId(group.getId(), memberId).isPresent()) {
			return new InviteJoinResponse(group.getId(), false);
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		// 동시 클릭의 유니크 위반을 이 트랜잭션 안에서 드러내려고 바로 flush한다
		groupMemberRepository.saveAndFlush(GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER));
		log.info("초대 링크 가입: groupId={}, memberId={}", group.getId(), memberId);
		return new InviteJoinResponse(group.getId(), true);
	}
}
