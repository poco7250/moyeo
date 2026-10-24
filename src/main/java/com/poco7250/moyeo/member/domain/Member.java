package com.poco7250.moyeo.member.domain;

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
 * 회원 엔티티.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	// BCrypt 해시값 저장 (평문 저장 금지)
	@Column(nullable = false, length = 100)
	private String password;

	@Column(nullable = false, unique = true, length = 20)
	private String nickname;

	private Member(String email, String password, String nickname) {
		this.email = email;
		this.password = password;
		this.nickname = nickname;
	}

	/**
	 * 신규 회원을 만든다. password는 이미 암호화된 값이어야 한다.
	 */
	public static Member create(String email, String encodedPassword, String nickname) {
		return new Member(email, encodedPassword, nickname);
	}
}
