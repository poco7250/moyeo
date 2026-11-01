package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.domain.InviteLink;
import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InviteJoinerTest {

	private static final String TOKEN = "t".repeat(43);
	private static final Long GROUP_ID = 1L;
	private static final Long MEMBER_ID = 7L;

	@Mock
	InviteLinkService inviteLinkService;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	MemberRepository memberRepository;

	@InjectMocks
	InviteJoiner inviteJoiner;

	private final Group group = Group.create("대학 동기");
	private final Member member = Member.ofKakao(1001L, "카카오닉", null, null);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
	}

	@Test
	void 신규_회원은_카카오_닉네임과_빈_이모지로_MEMBER_가입한다() {
		givenUsableLink();
		given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID)).willReturn(Optional.empty());
		given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
		given(groupMemberRepository.saveAndFlush(any(GroupMember.class)))
				.willAnswer(AdditionalAnswers.returnsFirstArg());

		InviteJoinResponse response = inviteJoiner.join(TOKEN, MEMBER_ID);

		assertThat(response).isEqualTo(new InviteJoinResponse(GROUP_ID, true));
		ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
		verify(groupMemberRepository).saveAndFlush(captor.capture());
		GroupMember saved = captor.getValue();
		assertThat(saved.getGroup()).isSameAs(group);
		assertThat(saved.getMember()).isSameAs(member);
		assertThat(saved.getNickname()).isEqualTo("카카오닉");
		assertThat(saved.getEmoji()).isNull();
		assertThat(saved.getRole()).isEqualTo(GroupRole.MEMBER);
	}

	@Test
	void 이미_멤버면_새로_만들지_않고_joined_false() {
		givenUsableLink();
		given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
				.willReturn(Optional.of(GroupMember.join(group, member, "카카오닉", GroupRole.MEMBER)));

		InviteJoinResponse response = inviteJoiner.join(TOKEN, MEMBER_ID);

		assertThat(response).isEqualTo(new InviteJoinResponse(GROUP_ID, false));
		verify(groupMemberRepository, never()).saveAndFlush(any());
		verifyNoInteractions(memberRepository);
	}

	@Test
	void 만료된_링크면_INVITE_LINK_EXPIRED이고_가입하지_않는다() {
		given(inviteLinkService.getUsableLink(TOKEN))
				.willThrow(new BusinessException(ErrorCode.INVITE_LINK_EXPIRED));

		assertThatThrownBy(() -> inviteJoiner.join(TOKEN, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVITE_LINK_EXPIRED);
		verifyNoInteractions(groupMemberRepository, memberRepository);
	}

	@Test
	void 회원이_없으면_MEMBER_NOT_FOUND() {
		givenUsableLink();
		given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID)).willReturn(Optional.empty());
		given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> inviteJoiner.join(TOKEN, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		verify(groupMemberRepository, never()).saveAndFlush(any());
	}

	private void givenUsableLink() {
		given(inviteLinkService.getUsableLink(TOKEN))
				.willReturn(InviteLink.issue(group, TOKEN, LocalDateTime.of(2026, 11, 8, 12, 0)));
	}
}
