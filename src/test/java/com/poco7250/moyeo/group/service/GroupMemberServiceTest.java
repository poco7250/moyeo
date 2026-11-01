package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.dto.GroupMemberUpdateRequest;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.member.domain.Member;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	GroupAccessChecker groupAccessChecker;

	@InjectMocks
	GroupMemberService groupMemberService;

	private final Group group = Group.create("대학 동기");
	private final Member owner = Member.ofKakao(1001L, "모여", null, null);
	private final Member friend = Member.ofKakao(1002L, "친구", null, null);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(owner, "id", 7L);
		ReflectionTestUtils.setField(friend, "id", 8L);
	}

	@Test
	void 멤버_목록은_회원_id_닉네임_이모지_역할을_준다() {
		given(groupMemberRepository.findAllByGroupIdOrderByIdAsc(1L)).willReturn(List.of(
				GroupMember.join(group, owner, "모여", GroupRole.OWNER),
				GroupMember.join(group, friend, "친구", GroupRole.MEMBER)));

		List<GroupMemberResponse> members = groupMemberService.getMembers(1L, 7L);

		assertThat(members).containsExactly(
				new GroupMemberResponse(7L, "모여", null, GroupRole.OWNER),
				new GroupMemberResponse(8L, "친구", null, GroupRole.MEMBER));
	}

	@Test
	void 비멤버는_멤버_목록을_볼_수_없다() {
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
				.given(groupAccessChecker).requireMember(1L, 9L);

		assertThatThrownBy(() -> groupMemberService.getMembers(1L, 9L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(groupMemberRepository);
	}

	@Test
	void 내_프로필을_바꾸면_닉네임은_공백을_지우고_이모지가_반영된다() {
		GroupMember me = GroupMember.join(group, owner, "모여", GroupRole.OWNER);
		given(groupMemberRepository.findByGroupIdAndMemberId(1L, 7L)).willReturn(Optional.of(me));

		GroupMemberResponse response = groupMemberService.updateMyProfile(
				1L, 7L, new GroupMemberUpdateRequest("  총무  ", "🐶"));

		assertThat(response).isEqualTo(new GroupMemberResponse(7L, "총무", "🐶", GroupRole.OWNER));
		assertThat(me.getNickname()).isEqualTo("총무");
	}

	@Test
	void 이모지를_공백으로_보내면_비운다() {
		GroupMember me = GroupMember.join(group, owner, "모여", GroupRole.OWNER);
		me.updateProfile("모여", "🐶");
		given(groupMemberRepository.findByGroupIdAndMemberId(1L, 7L)).willReturn(Optional.of(me));

		groupMemberService.updateMyProfile(1L, 7L, new GroupMemberUpdateRequest("모여", "  "));

		assertThat(me.getEmoji()).isNull();
	}

	@Test
	void 비멤버는_프로필을_바꿀_수_없다() {
		given(groupMemberRepository.findByGroupIdAndMemberId(1L, 9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> groupMemberService.updateMyProfile(
				1L, 9L, new GroupMemberUpdateRequest("친구", null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}
}
