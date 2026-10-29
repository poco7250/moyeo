package com.poco7250.moyeo.auth.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
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
 * 발급한 Refresh 토큰. 원문 대신 SHA-256 해시만 저장하고, 재발급할 때마다 지우고 새로 만든다(회전).
 * 한 회원이 여러 기기에서 로그인할 수 있어 회원당 여러 개를 가질 수 있다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	// SHA-256 hex
	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
		this.member = member;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	/**
	 * 회원에게 새 Refresh 토큰을 발급한다.
	 */
	public static RefreshToken issue(Member member, String tokenHash, LocalDateTime expiresAt) {
		return new RefreshToken(member, tokenHash, expiresAt);
	}

	/**
	 * 주어진 시각에 만료됐는지 확인한다. 만료 시각 정각부터 만료로 본다.
	 */
	public boolean isExpired(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}
}
