package com.poco7250.moyeo.push.domain;

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
 * 회원이 꺼 둔 푸시 종류. 기본은 전부 켜져 있어 끈 종류만 행으로 남긴다.
 * 종류 컬럼은 enum 추가 때 로컬 MySQL 네이티브 enum ALTER가 필요 없도록 문자열 컬럼으로 둔다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushMute extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
	private PushType type;

	private PushMute(Member member, PushType type) {
		this.member = member;
		this.type = type;
	}

	/**
	 * 회원의 한 종류 푸시를 끈다.
	 */
	public static PushMute of(Member member, PushType type) {
		return new PushMute(member, type);
	}
}
