package com.poco7250.moyeo.group.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 초대 링크. 토큰은 공개 URL에 그대로 들어가는 값이라 원문을 저장한다.
 * 재발급하면 기존 링크는 지우지 않고 revokedAt을 채워 폐기한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private Group group;

	// SecureRandom 32바이트 Base64URL(패딩 없음) = 43자
	@Column(nullable = false, unique = true, length = 43)
	private String token;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime revokedAt;

	private InviteLink(Group group, String token, LocalDateTime expiresAt) {
		this.group = group;
		this.token = token;
		this.expiresAt = expiresAt;
	}

	/**
	 * 그룹에 새 초대 링크를 발급한다.
	 */
	public static InviteLink issue(Group group, String token, LocalDateTime expiresAt) {
		return new InviteLink(group, token, expiresAt);
	}

	/**
	 * 링크를 폐기한다. 이미 폐기된 링크면 처음 폐기 시각을 유지한다.
	 */
	public void revoke(LocalDateTime now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	/**
	 * 주어진 시각에 쓸 수 있는 링크인지 확인한다. 만료 시각 정각부터 사용 불가다.
	 */
	public boolean isUsable(LocalDateTime now) {
		return revokedAt == null && now.isBefore(expiresAt);
	}
}
