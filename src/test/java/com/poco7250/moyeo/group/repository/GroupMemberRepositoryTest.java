package com.poco7250.moyeo.group.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class GroupMemberRepositoryTest {

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	private Member owner;
	private Member friend;
	private Group group;
	private Group otherGroup;

	@BeforeEach
	void setUp() {
		owner = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));
		friend = memberRepository.saveAndFlush(Member.ofKakao(1002L, "친구", null, null));
		group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		otherGroup = groupRepository.saveAndFlush(Group.create("회사 동기"));
	}

	@Test
	@DisplayName("같은 그룹에 같은 회원을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateGroupMember() {
		groupMemberRepository.saveAndFlush(GroupMember.join(group, owner, "모여", GroupRole.OWNER));

		assertThatThrownBy(() -> groupMemberRepository.saveAndFlush(
				GroupMember.join(group, owner, "모여2", GroupRole.MEMBER)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 회원이라도 다른 그룹에는 가입할 수 있다")
	void sameMemberInOtherGroup() {
		groupMemberRepository.saveAndFlush(GroupMember.join(group, owner, "모여", GroupRole.OWNER));
		groupMemberRepository.saveAndFlush(GroupMember.join(otherGroup, owner, "모여", GroupRole.MEMBER));

		assertThat(groupMemberRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("그룹 멤버 여부를 그룹 id와 회원 id로 확인한다")
	void existsByGroupIdAndMemberId() {
		groupMemberRepository.saveAndFlush(GroupMember.join(group, owner, "모여", GroupRole.OWNER));

		assertThat(groupMemberRepository.existsByGroupIdAndMemberId(group.getId(), owner.getId())).isTrue();
		assertThat(groupMemberRepository.existsByGroupIdAndMemberId(group.getId(), friend.getId())).isFalse();
		assertThat(groupMemberRepository.existsByGroupIdAndMemberId(otherGroup.getId(), owner.getId())).isFalse();
	}

	@Test
	@DisplayName("그룹 id로 그 그룹 멤버만 조회한다")
	void findAllByGroupId() {
		groupMemberRepository.saveAndFlush(GroupMember.join(group, owner, "모여", GroupRole.OWNER));
		groupMemberRepository.saveAndFlush(GroupMember.join(group, friend, "친구", GroupRole.MEMBER));
		groupMemberRepository.saveAndFlush(GroupMember.join(otherGroup, friend, "친구", GroupRole.OWNER));

		assertThat(groupMemberRepository.findAllByGroupId(group.getId()))
				.extracting(gm -> gm.getMember().getId(), GroupMember::getNickname, GroupMember::getRole)
				.containsExactlyInAnyOrder(
						tuple(owner.getId(), "모여", GroupRole.OWNER),
						tuple(friend.getId(), "친구", GroupRole.MEMBER));
		assertThat(groupMemberRepository.findAllByGroupId(group.getId()))
				.allSatisfy(gm -> assertThat(gm.getCreatedAt()).isNotNull());
	}

	@Test
	@DisplayName("회원이 가입한 그룹만 최근 가입 순으로 그룹과 함께 조회한다")
	void findAllWithGroupByMemberId() {
		groupMemberRepository.saveAndFlush(GroupMember.join(group, owner, "모여", GroupRole.OWNER));
		groupMemberRepository.saveAndFlush(GroupMember.join(otherGroup, owner, "모여", GroupRole.MEMBER));
		groupMemberRepository.saveAndFlush(GroupMember.join(group, friend, "친구", GroupRole.MEMBER));

		assertThat(groupMemberRepository.findAllWithGroupByMemberId(owner.getId()))
				.extracting(gm -> gm.getGroup().getName())
				.containsExactly("회사 동기", "대학 동기");
		assertThat(groupMemberRepository.findAllWithGroupByMemberId(9999L)).isEmpty();
	}
}
