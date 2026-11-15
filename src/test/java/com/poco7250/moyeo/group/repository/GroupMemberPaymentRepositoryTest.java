package com.poco7250.moyeo.group.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupMemberPayment;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.util.List;
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
class GroupMemberPaymentRepositoryTest {

	@Autowired
	GroupMemberPaymentRepository paymentRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	private Group group;
	private Group otherGroup;
	private Member chulsoo;
	private Member jisoo;
	private GroupMember chulsooInGroup;

	@BeforeEach
	void setUp() {
		group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		otherGroup = groupRepository.saveAndFlush(Group.create("회사"));
		chulsoo = memberRepository.saveAndFlush(Member.ofKakao(1L, "철수", null, null));
		jisoo = memberRepository.saveAndFlush(Member.ofKakao(2L, "지수", null, null));
		chulsooInGroup = join(group, chulsoo);
	}

	@Test
	@DisplayName("그룹 멤버 1명에 송금 정보를 두 번 만들면 유니크 위반이다")
	void onePerGroupMember() {
		paymentRepository.saveAndFlush(link(chulsooInGroup, "https://toss.me/a"));

		assertThatThrownBy(() -> paymentRepository.saveAndFlush(link(chulsooInGroup, "https://toss.me/b")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("그룹·회원 목록으로 조회하면 그 그룹의 송금 정보만 나온다")
	void findByGroupAndMembers() {
		paymentRepository.saveAndFlush(link(chulsooInGroup, "https://toss.me/a"));
		paymentRepository.saveAndFlush(link(join(group, jisoo), "https://toss.me/b"));
		paymentRepository.saveAndFlush(link(join(otherGroup, chulsoo), "https://toss.me/other"));

		assertThat(paymentRepository.findAllByGroupIdAndMemberIdIn(group.getId(), List.of(chulsoo.getId())))
				.extracting(GroupMemberPayment::getTossLink).containsExactly("https://toss.me/a");
		assertThat(paymentRepository.findAllByGroupIdAndMemberIdIn(group.getId(),
				List.of(chulsoo.getId(), jisoo.getId()))).hasSize(2);
		assertThat(paymentRepository.findByGroupMemberId(chulsooInGroup.getId())).isPresent();
	}

	private GroupMember join(Group target, Member member) {
		return groupMemberRepository.saveAndFlush(GroupMember.join(target, member, member.getNickname(),
				GroupRole.MEMBER));
	}

	private static GroupMemberPayment link(GroupMember groupMember, String tossLink) {
		return GroupMemberPayment.create(groupMember, null, null, null, tossLink, null);
	}
}
