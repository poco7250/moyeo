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
 * 회원 엔티티. 카카오 로그인으로만 가입하며 kakaoId로 식별한다.
 * 닉네임은 카카오 닉네임이라 중복될 수 있고, 그룹 안 별명은 GroupMember에 따로 둔다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private Long kakaoId;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Column(length = 2048)
	private String profileImageUrl;

	// 카카오 동의 항목이라 없을 수 있음
	@Column(length = 100)
	private String email;

	private Member(Long kakaoId, String nickname, String profileImageUrl, String email) {
		this.kakaoId = kakaoId;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.email = email;
	}

	/**
	 * 카카오 사용자 정보로 신규 회원을 만든다.
	 */
	public static Member ofKakao(Long kakaoId, String nickname, String profileImageUrl, String email) {
		return new Member(kakaoId, nickname, profileImageUrl, email);
	}

	/**
	 * 로그인할 때 받은 카카오 최신 프로필로 덮어쓴다. 동의를 철회한 항목은 null이 된다.
	 */
	public void updateKakaoProfile(String nickname, String profileImageUrl, String email) {
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.email = email;
	}
}
