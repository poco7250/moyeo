package com.poco7250.moyeo.group.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 친구 모임 그룹. 멤버는 GroupMember로, 초대는 InviteLink로 관리한다.
 */
@Getter
@Entity
// GROUP·GROUPS 모두 MySQL 8 예약어라 테이블명을 따로 둔다
@Table(name = "friend_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	private Group(String name) {
		this.name = name;
	}

	/**
	 * 새 그룹을 만든다.
	 */
	public static Group create(String name) {
		return new Group(name);
	}
}
