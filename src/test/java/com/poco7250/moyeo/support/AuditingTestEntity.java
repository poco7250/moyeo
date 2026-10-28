package com.poco7250.moyeo.support;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BaseTimeEntity 감사 필드 검증용 테스트 전용 엔티티.
 * 도메인 엔티티가 바뀌어도 감사 테스트가 흔들리지 않도록 따로 둔다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditingTestEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	public AuditingTestEntity(String name) {
		this.name = name;
	}

	/**
	 * 이름을 바꾼다. 수정 시각 갱신 확인용.
	 */
	public void rename(String name) {
		this.name = name;
	}
}
