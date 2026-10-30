package com.poco7250.moyeo.auth.service;

import com.poco7250.moyeo.auth.client.KakaoOAuthClient;
import com.poco7250.moyeo.auth.dto.KakaoUserInfo;
import com.poco7250.moyeo.auth.dto.TokenPair;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 카카오 로그인 흐름(인가 코드 → 카카오 사용자 정보 → 회원 조회·생성 → 자체 토큰 발급).
 * 카카오 HTTP 호출이 DB 트랜잭션을 붙잡지 않도록 여기서는 트랜잭션을 열지 않고,
 * 회원 저장·토큰 발급은 각 서비스가 자기 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final KakaoOAuthClient kakaoOAuthClient;
	private final MemberService memberService;
	private final TokenService tokenService;

	/**
	 * 카카오 인가 코드로 로그인하고 토큰 쌍을 발급한다.
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 카카오 인증 실패·장애 시(KakaoOAuthClient 참고)
	 */
	public TokenPair loginWithKakao(String authorizationCode) {
		KakaoUserInfo userInfo = kakaoOAuthClient.fetchUserInfo(authorizationCode);
		MemberResponse member = memberService.findOrCreateByKakaoId(userInfo.toProfile());
		return tokenService.issue(member.id());
	}
}
