package com.poco7250.moyeo.group.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹에 가입한 회원. 그룹 안에서 쓰는 닉네임·프로필 이모지를 따로 가진다.
 * 같은 초대 링크를 동시에 눌러도 중복 가입되지 않도록 (group, member)에 유니크 제약을 건다.
 */
@Getter
@Entity
@Table(
		name = "group_member",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_group_member_group_id_member_id",
				columnNames = {"group_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private Group group;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, length = 50)
	private String nickname;

	// 이모지 기본값·허용 범위는 PRD 미정(Task 009)이라 비워 둘 수 있다
	@Column(length = 16)
	private String emoji;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GroupRole role;

	private GroupMember(Group group, Member member, String nickname, GroupRole role) {
		this.group = group;
		this.member = member;
		this.nickname = nickname;
		this.role = role;
	}

	/**
	 * 회원을 그룹에 가입시킨다.
	 */
	public static GroupMember join(Group group, Member member, String nickname, GroupRole role) {
		return new GroupMember(group, member, nickname, role);
	}
}
