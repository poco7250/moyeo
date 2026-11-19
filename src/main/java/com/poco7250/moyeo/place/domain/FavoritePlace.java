package com.poco7250.moyeo.place.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 단골 장소(PRD P-8). 값은 사용자가 입력한 장소뿐이라 카카오 데이터 무저장 원칙과 충돌하지 않는다.
 * 같은 장소 중복은 지도 링크가 길어(2,048자) 유니크 인덱스 대신 서비스가 그룹 잠금 안에서 막는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePlace extends BaseTimeEntity {

	public static final int MAX_PER_GROUP = 50;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private Group group;

	@Embedded
	private Place place;

	// 단골로 저장한 회원
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_id", nullable = false)
	private Member createdBy;

	private FavoritePlace(Group group, Place place, Member createdBy) {
		this.group = group;
		this.place = place;
		this.createdBy = createdBy;
	}

	/**
	 * 그룹 단골 장소를 만든다.
	 */
	public static FavoritePlace save(Group group, Place place, Member createdBy) {
		return new FavoritePlace(group, place, createdBy);
	}
}
