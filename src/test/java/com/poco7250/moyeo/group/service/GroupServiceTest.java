package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupCreateRequest;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.group.dto.InviteLinkResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

	@Mock
	GroupRepository groupRepository;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	InviteLinkService inviteLinkService;

	@InjectMocks
	GroupService groupService;

	private final Member member = Member.ofKakao(1001L, "카카오닉", null, null);
	private final InviteLinkResponse inviteLink =
			new InviteLinkResponse("t".repeat(43), "/invites/" + "t".repeat(43), LocalDateTime.of(2026, 11, 8, 12, 0));

	@Test
	void 그룹을_만들면_생성자가_카카오_닉네임으로_OWNER_멤버가_되고_초대_링크가_발급된다() {
		given(memberRepository.findById(7L)).willReturn(Optional.of(member));
		given(groupRepository.save(any(Group.class))).willAnswer(AdditionalAnswers.returnsFirstArg());
		given(inviteLinkService.issue(any(Group.class))).willReturn(inviteLink);

		GroupCreateResponse response = groupService.create(7L, new GroupCreateRequest("대학 동기"));

		ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
		verify(groupMemberRepository).save(captor.capture());
		GroupMember owner = captor.getValue();
		assertThat(owner.getMember()).isSameAs(member);
		assertThat(owner.getNickname()).isEqualTo("카카오닉");
		assertThat(owner.getRole()).isEqualTo(GroupRole.OWNER);
		assertThat(owner.getGroup().getName()).isEqualTo("대학 동기");
		verify(inviteLinkService).issue(owner.getGroup());

		assertThat(response.group().name()).isEqualTo("대학 동기");
		assertThat(response.inviteLink()).isEqualTo(inviteLink);
		assertThat(response.inviteLink().expiresAt()).isNotNull();
	}

	@Test
	void 회원이_없으면_MEMBER_NOT_FOUND이고_그룹을_만들지_않는다() {
		given(memberRepository.findById(7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> groupService.create(7L, new GroupCreateRequest("대학 동기")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		verify(groupRepository, never()).save(any());
	}

	@Test
	void 내_그룹_목록은_멤버십의_그룹을_응답으로_바꾼다() {
		Group group = Group.create("대학 동기");
		given(groupMemberRepository.findAllWithGroupByMemberId(7L))
				.willReturn(List.of(GroupMember.join(group, member, "카카오닉", GroupRole.OWNER)));

		assertThat(groupService.getMyGroups(7L))
				.singleElement()
				.satisfies(g -> assertThat(g.name()).isEqualTo("대학 동기"));
	}
}
