package com.poco7250.moyeo.member.service;

import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.dto.KakaoProfile;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * kakaoId 기준 회원 생성·갱신을 독립 트랜잭션으로 처리한다.
 * 유니크 위반이 나도 호출자 트랜잭션을 오염시키지 않도록 REQUIRES_NEW로 분리했다(재시도는 MemberService가 한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberKakaoRegistrar {

	private final MemberRepository memberRepository;

	/**
	 * 회원이 있으면 프로필을 갱신하고, 없으면 새로 만든다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public MemberResponse registerOrUpdate(KakaoProfile profile) {
		Member member = memberRepository.findByKakaoId(profile.kakaoId())
				.map(existing -> update(existing, profile))
				.orElseGet(() -> register(profile));
		return MemberResponse.from(member);
	}

	/**
	 * 기존 회원의 프로필을 카카오 최신 값으로 바꾼다.
	 */
	private Member update(Member member, KakaoProfile profile) {
		member.updateKakaoProfile(profile.nickname(), profile.profileImageUrl(), profile.email());
		return member;
	}

	/**
	 * 신규 회원을 저장한다. 유니크 위반을 이 트랜잭션 안에서 드러내려고 바로 flush한다.
	 */
	private Member register(KakaoProfile profile) {
		Member saved = memberRepository.saveAndFlush(Member.ofKakao(
				profile.kakaoId(), profile.nickname(), profile.profileImageUrl(), profile.email()));
		log.info("카카오 회원 생성: memberId={}", saved.getId());
		return saved;
	}
}
