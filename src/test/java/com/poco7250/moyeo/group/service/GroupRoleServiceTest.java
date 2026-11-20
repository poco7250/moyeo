package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.member.domain.Member;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupRoleServiceTest {

	private static final Long GROUP_ID = 1L;
	private static final Long OWNER_ID = 7L;
	private static final Long MEMBER_ID = 8L;
	private static final Long OUTSIDER_ID = 99L;

	@Mock
	GroupAccessChecker groupAccessChecker;

	@Mock
	GroupRepository groupRepository;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@InjectMocks
	GroupRoleService groupRoleService;

	private final Group group = Group.create("대학 동기");
	private GroupMember owner;
	private GroupMember member;

	@BeforeEach
	void setUp() {
		owner = GroupMember.join(group, member(OWNER_ID), "철수", GroupRole.OWNER);
		member = GroupMember.join(group, member(MEMBER_ID), "민수", GroupRole.MEMBER);
	}

	@Test
	@DisplayName("방장이 멤버를 총무로 지정하고 다시 멤버로 되돌린다")
	void changeRole() {
		givenLocked(owner, member);

		GroupMemberResponse treasurer = groupRoleService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID,
				GroupRole.TREASURER);
		assertThat(treasurer.role()).isEqualTo(GroupRole.TREASURER);

		GroupMemberResponse plain = groupRoleService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, GroupRole.MEMBER);
		assertThat(plain.role()).isEqualTo(GroupRole.MEMBER);
	}

	@Test
	@DisplayName("같은 역할로 바꾸면 그대로 돌려준다")
	void sameRoleIsNoop() {
		givenLocked(owner, member);

		assertThat(groupRoleService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, GroupRole.MEMBER).role())
				.isEqualTo(GroupRole.MEMBER);
	}

	@Test
	@DisplayName("방장이 아니면 403 NOT_GROUP_OWNER이고 대상을 읽지 않는다")
	void notOwner() {
		GroupMember treasurer = GroupMember.join(group, member(MEMBER_ID), "민수", GroupRole.TREASURER);
		givenLocked(treasurer);

		assertRejected(() -> groupRoleService.changeRole(GROUP_ID, MEMBER_ID, OWNER_ID, GroupRole.MEMBER),
				ErrorCode.NOT_GROUP_OWNER);
		assertRejected(() -> groupRoleService.transferOwner(GROUP_ID, MEMBER_ID, OWNER_ID),
				ErrorCode.NOT_GROUP_OWNER);
		assertThat(owner.getRole()).isEqualTo(GroupRole.OWNER);
	}

	@Test
	@DisplayName("OWNER로 바꾸거나 방장을 대상으로 바꾸면 409 GROUP_ROLE_INVALID_CHANGE")
	void invalidChange() {
		givenLocked(owner, member);

		assertRejected(() -> groupRoleService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, GroupRole.OWNER),
				ErrorCode.GROUP_ROLE_INVALID_CHANGE);
		assertRejected(() -> groupRoleService.changeRole(GROUP_ID, OWNER_ID, OWNER_ID, GroupRole.MEMBER),
				ErrorCode.GROUP_ROLE_INVALID_CHANGE);
		assertRejected(() -> groupRoleService.transferOwner(GROUP_ID, OWNER_ID, OWNER_ID),
				ErrorCode.GROUP_ROLE_INVALID_CHANGE);
		assertThat(owner.getRole()).isEqualTo(GroupRole.OWNER);
		assertThat(member.getRole()).isEqualTo(GroupRole.MEMBER);
	}

	@Test
	@DisplayName("대상이 그룹 멤버가 아니면 404 GROUP_MEMBER_NOT_FOUND")
	void targetNotMember() {
		givenLocked(owner);
		given(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, OUTSIDER_ID))
				.willReturn(Optional.empty());

		assertRejected(() -> groupRoleService.changeRole(GROUP_ID, OWNER_ID, OUTSIDER_ID, GroupRole.TREASURER),
				ErrorCode.GROUP_MEMBER_NOT_FOUND);
		assertRejected(() -> groupRoleService.transferOwner(GROUP_ID, OWNER_ID, OUTSIDER_ID),
				ErrorCode.GROUP_MEMBER_NOT_FOUND);
	}

	@Test
	@DisplayName("방장을 넘기면 새 방장은 OWNER, 이전 방장은 MEMBER가 된다(총무였어도)")
	void transferOwner() {
		member.changeRole(GroupRole.TREASURER);
		givenLocked(owner, member);

		List<GroupMemberResponse> changed = groupRoleService.transferOwner(GROUP_ID, OWNER_ID, MEMBER_ID);

		assertThat(changed).extracting(GroupMemberResponse::memberId, GroupMemberResponse::role)
				.containsExactly(tuple(OWNER_ID, GroupRole.MEMBER),
						tuple(MEMBER_ID, GroupRole.OWNER));
	}

	@Test
	@DisplayName("비멤버면 403 NOT_GROUP_MEMBER이고 그룹을 잠그지 않는다")
	void outsider() {
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
				.given(groupAccessChecker).requireMember(GROUP_ID, OUTSIDER_ID);

		assertRejected(() -> groupRoleService.transferOwner(GROUP_ID, OUTSIDER_ID, MEMBER_ID),
				ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(groupRepository, groupMemberRepository);
	}

	/**
	 * 그룹 행 잠금과 주어진 멤버십들의 잠금 읽기를 준비한다.
	 */
	private void givenLocked(GroupMember... memberships) {
		given(groupRepository.findByIdForUpdate(GROUP_ID)).willReturn(Optional.of(group));
		for (GroupMember membership : memberships) {
			lenient().when(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID,
					membership.getMember().getId())).thenReturn(Optional.of(membership));
		}
	}

	private void assertRejected(ThrowingCallable call, ErrorCode errorCode) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}

	private static Member member(Long id) {
		Member member = Member.ofKakao(1000L + id, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
