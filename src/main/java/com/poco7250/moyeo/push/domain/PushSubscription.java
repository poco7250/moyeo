package com.poco7250.moyeo.push.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.push.PushEndpoint;
import com.poco7250.moyeo.global.push.PushTarget;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 기기 하나의 웹 푸시 구독(브라우저 PushSubscription). 회원당 여러 기기를 둘 수 있다.
 * endpoint가 길어(2,048자) 유니크 인덱스는 SHA-256 해시 컬럼에 건다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

	/** 회원당 기기 수 상한. 넘으면 가장 오래전에 등록한 기기부터 지운다 */
	public static final int MAX_PER_MEMBER = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, length = PushEndpoint.MAX_LENGTH)
	private String endpoint;

	@Column(nullable = false, unique = true, length = 64)
	private String endpointHash;

	@Column(nullable = false, length = 100)
	private String p256dh;

	@Column(nullable = false, length = 50)
	private String auth;

	// 마지막으로 등록(갱신)한 시각. 기기 수 상한을 넘을 때 오래된 순서를 정한다
	@Column(nullable = false)
	private LocalDateTime registeredAt;

	private PushSubscription(Member member, String endpoint, String p256dh, String auth, LocalDateTime now) {
		this.member = member;
		this.endpoint = endpoint;
		this.endpointHash = hash(endpoint);
		this.p256dh = p256dh;
		this.auth = auth;
		this.registeredAt = now;
	}

	/**
	 * 새 구독을 만든다.
	 */
	public static PushSubscription register(Member member, String endpoint, String p256dh, String auth,
			LocalDateTime now) {
		return new PushSubscription(member, endpoint, p256dh, auth, now);
	}

	/**
	 * 같은 기기를 다시 등록하면 키를 갱신하고 지금 로그인한 회원 것으로 옮긴다(한 기기에서 계정을 바꾼 경우).
	 */
	public void renew(Member owner, String p256dh, String auth, LocalDateTime now) {
		this.member = owner;
		this.p256dh = p256dh;
		this.auth = auth;
		this.registeredAt = now;
	}

	/**
	 * 전송 대상 값으로 바꾼다.
	 */
	public PushTarget toTarget() {
		return new PushTarget(endpoint, p256dh, auth);
	}

	/**
	 * endpoint의 SHA-256 16진수(64자). 유니크 조회 키로 쓴다.
	 */
	public static String hash(String endpoint) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
		}
	}
}
