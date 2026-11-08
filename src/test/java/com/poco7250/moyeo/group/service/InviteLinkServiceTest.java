package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.poco7250.moyeo.support.TestClocks;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InviteLinkServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0, 0);
	private static final String NEW_TOKEN = "n".repeat(43);

	@Mock
	InviteLinkRepository inviteLinkRepository;

	@Mock
	GroupRepository groupRepository;

	@Mock
	InviteTokenGenerator inviteTokenGenerator;

	@Mock
	GroupAccessChecker groupAccessChecker;

	@Mock
	GroupMemberRepository groupMemberRepository;

	InviteLinkService inviteLinkService;

	private final Group group = Group.create("대학 동기");

	@BeforeEach
	void setUp() {
		inviteLinkService = new InviteLinkService(inviteLinkRepository, groupRepository, groupMemberRepository, inviteTokenGenerator,
				groupAccessChecker, new InviteLinkProperties(Duration.ofDays(7), ""), new ShareLinkProperties(""),
				TestClocks.fixed(NOW));
	}

	@Test
	void 발급한_링크는_7일_뒤_만료되고_공유_URL을_준다() {
		given(inviteTokenGenerator.generate()).willReturn(NEW_TOKEN);
		given(inviteLinkRepository.save(any(InviteLink.class))).willAnswer(AdditionalAnswers.returnsFirstArg());

		InviteLinkResponse response = inviteLinkService.issue(group);

		assertThat(response.token()).isEqualTo(NEW_TOKEN);
		assertThat(response.url()).isEqualTo("/share/invites/" + NEW_TOKEN);
		assertThat(response.expiresAt()).isEqualTo(NOW.plusDays(7));
	}

	@Test
	void 재발급하면_이전_링크는_쓸_수_없고_새_링크는_쓸_수_있다() {
		InviteLink oldLink = InviteLink.issue(group, "o".repeat(43), NOW.plusDays(3));
		given(groupRepository.findById(1L)).willReturn(Optional.of(group));
		given(inviteLinkRepository.findAllByGroupIdAndRevokedAtIsNull(1L)).willReturn(List.of(oldLink));
		given(inviteTokenGenerator.generate()).willReturn(NEW_TOKEN);
		given(inviteLinkRepository.save(any(InviteLink.class))).willAnswer(AdditionalAnswers.returnsFirstArg());

		InviteLinkResponse response = inviteLinkService.reissue(1L, 7L);

		assertThat(oldLink.isUsable(NOW)).isFalse();
		assertThat(oldLink.getRevokedAt()).isEqualTo(NOW);
		assertThat(response.token()).isEqualTo(NEW_TOKEN);
		assertThat(response.expiresAt()).isAfter(NOW);
	}

	@Test
	void 비멤버가_재발급하면_NOT_GROUP_MEMBER이고_링크를_건드리지_않는다() {
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
				.given(groupAccessChecker).requireMember(1L, 8L);

		assertThatThrownBy(() -> inviteLinkService.reissue(1L, 8L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verify(inviteLinkRepository, never()).save(any());
	}

	@Test
	void 그룹이_없으면_GROUP_NOT_FOUND() {
		given(groupRepository.findById(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> inviteLinkService.reissue(1L, 7L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.GROUP_NOT_FOUND);
	}

	@Test
	void 미리보기는_그룹_이름과_멤버_수와_만료_시각을_준다() {
		ReflectionTestUtils.setField(group, "id", 1L);
		InviteLink link = InviteLink.issue(group, NEW_TOKEN, NOW.plusDays(3));
		given(inviteLinkRepository.findWithGroupByToken(NEW_TOKEN)).willReturn(Optional.of(link));
		given(groupMemberRepository.countByGroupId(1L)).willReturn(4L);

		InvitePreviewResponse response = inviteLinkService.getPreview(NEW_TOKEN);

		assertThat(response.groupName()).isEqualTo("대학 동기");
		assertThat(response.memberCount()).isEqualTo(4L);
		assertThat(response.expiresAt()).isEqualTo(NOW.plusDays(3));
	}

	@Test
	void 없는_토큰이면_INVITE_LINK_NOT_FOUND() {
		given(inviteLinkRepository.findWithGroupByToken(NEW_TOKEN)).willReturn(Optional.empty());

		assertThatThrownBy(() -> inviteLinkService.getPreview(NEW_TOKEN))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVITE_LINK_NOT_FOUND);
	}

	@Test
	void 만료_시각_정각이면_INVITE_LINK_EXPIRED() {
		given(inviteLinkRepository.findWithGroupByToken(NEW_TOKEN))
				.willReturn(Optional.of(InviteLink.issue(group, NEW_TOKEN, NOW)));

		assertThatThrownBy(() -> inviteLinkService.getUsableLink(NEW_TOKEN))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVITE_LINK_EXPIRED);
	}

	@Test
	void 폐기된_링크면_INVITE_LINK_EXPIRED() {
		InviteLink link = InviteLink.issue(group, NEW_TOKEN, NOW.plusDays(3));
		link.revoke(NOW.minusHours(1));
		given(inviteLinkRepository.findWithGroupByToken(NEW_TOKEN)).willReturn(Optional.of(link));

		assertThatThrownBy(() -> inviteLinkService.getUsableLink(NEW_TOKEN))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVITE_LINK_EXPIRED);
	}
}
