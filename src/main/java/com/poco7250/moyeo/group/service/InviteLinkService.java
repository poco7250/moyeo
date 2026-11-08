package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.config.ShareLinkProperties;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.config.InviteLinkProperties;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.InviteLink;
import com.poco7250.moyeo.group.dto.InviteLinkResponse;
import com.poco7250.moyeo.group.dto.InvitePreviewResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.group.repository.InviteLinkRepository;
import com.poco7250.moyeo.group.token.InviteTokenGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크 발급·재발급·미리보기.
 */
@Service
@RequiredArgsConstructor
public class InviteLinkService {

	private final InviteLinkRepository inviteLinkRepository;
	private final GroupRepository groupRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final InviteTokenGenerator inviteTokenGenerator;
	private final GroupAccessChecker groupAccessChecker;
	private final InviteLinkProperties inviteLinkProperties;
	private final ShareLinkProperties shareLinkProperties;
	private final Clock clock;

	/**
	 * 그룹에 새 초대 링크를 발급한다. 만료 시각 = 지금 + 설정 TTL. 호출한 쪽 트랜잭션에 참여한다.
	 */
	@Transactional
	public InviteLinkResponse issue(Group group) {
		LocalDateTime expiresAt = LocalDateTime.now(clock).plus(inviteLinkProperties.ttl());
		InviteLink link = inviteLinkRepository.save(
				InviteLink.issue(group, inviteTokenGenerator.generate(), expiresAt));
		return InviteLinkResponse.of(link, shareLinkProperties.inviteUrl(link.getToken()));
	}

	/**
	 * 기존 링크를 모두 폐기하고 새 링크를 발급한다. 그룹 멤버라면 누구나 할 수 있다.
	 * 동시에 재발급하면 유효 링크가 둘 남을 수 있지만 둘 다 정상 링크라 MVP에서는 락을 걸지 않는다.
	 *
	 * @throws BusinessException 멤버가 아니면 NOT_GROUP_MEMBER, 그룹이 없으면 GROUP_NOT_FOUND
	 */
	@Transactional
	public InviteLinkResponse reissue(Long groupId, Long memberId) {
		groupAccessChecker.requireMember(groupId, memberId);
		Group group = groupRepository.findById(groupId)
				.orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

		LocalDateTime now = LocalDateTime.now(clock);
		inviteLinkRepository.findAllByGroupIdAndRevokedAtIsNull(groupId)
				.forEach(link -> link.revoke(now));
		return issue(group);
	}

	/**
	 * 초대 진입 화면용 정보를 준다. 비로그인 사용자도 호출한다.
	 *
	 * @throws BusinessException 없는 토큰이면 INVITE_LINK_NOT_FOUND, 만료·폐기면 INVITE_LINK_EXPIRED
	 */
	@Transactional(readOnly = true)
	public InvitePreviewResponse getPreview(String token) {
		InviteLink link = getUsableLink(token);
		Group group = link.getGroup();
		return new InvitePreviewResponse(
				group.getName(), groupMemberRepository.countByGroupId(group.getId()), link.getExpiresAt());
	}

	/**
	 * 지금 쓸 수 있는 초대 링크를 그룹과 함께 찾는다. 호출한 쪽 트랜잭션에 참여한다.
	 *
	 * @throws BusinessException 없는 토큰이면 INVITE_LINK_NOT_FOUND, 만료·폐기면 INVITE_LINK_EXPIRED
	 */
	@Transactional(readOnly = true)
	public InviteLink getUsableLink(String token) {
		InviteLink link = inviteLinkRepository.findWithGroupByToken(token)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));
		if (!link.isUsable(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.INVITE_LINK_EXPIRED);
		}
		return link;
	}
}
