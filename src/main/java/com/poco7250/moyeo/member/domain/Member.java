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
import org.springframework.util.StringUtils;

/**
 * 회원 엔티티. 카카오 로그인으로만 가입하며 kakaoId로 식별한다.
 * 닉네임은 카카오 닉네임이라 중복될 수 있고, 그룹 안 별명은 GroupMember에 따로 둔다.
 * 카카오가 닉네임을 주지 않으면(동의항목 미설정·철회) 신규 회원은 기본 닉네임, 기존 회원은 원래 닉네임을 쓴다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	/** 카카오 닉네임이 없을 때 신규 회원에게 붙이는 닉네임 */
	public static final String DEFAULT_NICKNAME = "모여 회원";

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
		this.nickname = StringUtils.hasText(nickname) ? nickname : DEFAULT_NICKNAME;
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
	 * 로그인할 때 받은 카카오 최신 프로필로 덮어쓴다. 동의를 철회한 항목은 null이 되지만, 닉네임은 비어 있으면 기존 값을 유지한다.
	 */
	public void updateKakaoProfile(String nickname, String profileImageUrl, String email) {
		if (StringUtils.hasText(nickname)) {
			this.nickname = nickname;
		}
		this.profileImageUrl = profileImageUrl;
		this.email = email;
	}
}
