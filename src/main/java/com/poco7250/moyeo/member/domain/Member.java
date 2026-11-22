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
 * 모임 게스트(PRD G-6)도 게임·벌칙 기록을 그대로 쓰려고 이 테이블에 둔다. 게스트는 kakaoId가 없어 로그인할 수 없고
 * 그룹 멤버십도 없어 어떤 그룹·모임 API에도 접근하지 못한다.
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

	// 게스트는 카카오 계정이 없어 null이다
	@Column(unique = true)
	private Long kakaoId;

	// 호스트가 모임에 이름만으로 추가한 게스트인지(PRD G-6)
	@Column(nullable = false)
	private boolean guest;

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
	 * 모임 게스트를 만든다. 이름은 앞뒤 공백을 뺀 값이고 비어 있으면 안 된다.
	 */
	public static Member guest(String name) {
		if (!StringUtils.hasText(name)) {
			throw new IllegalArgumentException("게스트 이름은 비어 있을 수 없습니다.");
		}
		Member member = new Member(null, name.strip(), null, null);
		member.guest = true;
		return member;
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
