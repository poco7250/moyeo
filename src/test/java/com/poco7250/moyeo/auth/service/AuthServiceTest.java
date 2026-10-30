package com.poco7250.moyeo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.auth.client.KakaoOAuthClient;
import com.poco7250.moyeo.auth.dto.KakaoUserInfo;
import com.poco7250.moyeo.auth.dto.TokenPair;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.dto.KakaoProfile;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AuthService 단위 테스트. 협력 객체는 모두 목이다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String CODE = "kakao-code";

	@Mock
	KakaoOAuthClient kakaoOAuthClient;

	@Mock
	MemberService memberService;

	@Mock
	TokenService tokenService;

	@InjectMocks
	AuthService authService;

	@Test
	@DisplayName("카카오 사용자 정보로 회원을 찾거나 만든 뒤 그 회원 ID로 토큰을 발급한다")
	void loginWithKakao() {
		KakaoUserInfo userInfo = new KakaoUserInfo(123L, "모여", "https://img/p.png", null);
		TokenPair tokens = new TokenPair("access", "refresh");
		given(kakaoOAuthClient.fetchUserInfo(CODE)).willReturn(userInfo);
		given(memberService.findOrCreateByKakaoId(userInfo.toProfile()))
				.willReturn(new MemberResponse(7L, "모여", "https://img/p.png"));
		given(tokenService.issue(7L)).willReturn(tokens);

		TokenPair result = authService.loginWithKakao(CODE);

		assertThat(result).isSameAs(tokens);
		InOrder order = inOrder(kakaoOAuthClient, memberService, tokenService);
		order.verify(kakaoOAuthClient).fetchUserInfo(CODE);
		order.verify(memberService).findOrCreateByKakaoId(new KakaoProfile(123L, "모여", "https://img/p.png", null));
		order.verify(tokenService).issue(7L);
	}

	@Test
	@DisplayName("카카오 인증이 실패하면 회원 처리·토큰 발급 없이 예외를 그대로 던진다")
	void kakaoFailure() {
		given(kakaoOAuthClient.fetchUserInfo(CODE)).willThrow(new BusinessException(ErrorCode.KAKAO_AUTH_FAILED));

		assertThatThrownBy(() -> authService.loginWithKakao(CODE))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.KAKAO_AUTH_FAILED);
		verify(memberService, never()).findOrCreateByKakaoId(any());
		verify(tokenService, never()).issue(anyLong());
	}
}
