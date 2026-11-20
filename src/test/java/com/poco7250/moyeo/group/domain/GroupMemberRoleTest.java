package com.poco7250.moyeo.group.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupMemberRoleTest {

	private final Group group = Group.create("대학 동기");
	private final Member member = Member.ofKakao(1001L, "민수", null, null);

	@Test
	@DisplayName("방장은 호스트·정산 권한, 총무는 정산 권한만, 멤버는 둘 다 없다")
	void roleAuthority() {
		assertThat(GroupRole.OWNER.hasHostAuthority()).isTrue();
		assertThat(GroupRole.OWNER.hasSettlementAuthority()).isTrue();
		assertThat(GroupRole.TREASURER.hasHostAuthority()).isFalse();
		assertThat(GroupRole.TREASURER.hasSettlementAuthority()).isTrue();
		assertThat(GroupRole.MEMBER.hasHostAuthority()).isFalse();
		assertThat(GroupRole.MEMBER.hasSettlementAuthority()).isFalse();
	}

	@Test
	@DisplayName("역할을 바꾸면 방장 여부도 따라 바뀐다")
	void changeRole() {
		GroupMember groupMember = GroupMember.join(group, member, "민수", GroupRole.MEMBER);
		assertThat(groupMember.isOwner()).isFalse();

		groupMember.changeRole(GroupRole.OWNER);
		assertThat(groupMember.isOwner()).isTrue();

		groupMember.changeRole(GroupRole.TREASURER);
		assertThat(groupMember.getRole()).isEqualTo(GroupRole.TREASURER);
		assertThat(groupMember.isOwner()).isFalse();
	}
}
