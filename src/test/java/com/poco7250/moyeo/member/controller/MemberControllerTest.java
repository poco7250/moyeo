package com.poco7250.moyeo.member.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.service.MemberService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import com.poco7250.moyeo.support.TestClocks;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MemberController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 회원 서비스는 목이다.
 */
@WebMvcTest(controllers = MemberController.class)
@Import(SecurityTestConfig.class)
class MemberControllerTest {

	private static final String ME_URL = "/api/members/me";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	JwtProperties jwtProperties;

	@MockitoBean
	MemberService memberService;

	@Test
	@DisplayName("유효한 토큰 쿠키면 내 닉네임·프로필 이미지를 공통 응답으로 준다")
	void me() throws Exception {
		given(memberService.getMe(7L)).willReturn(new MemberResponse(7L, "모여", "https://img.kakao.com/a.png"));

		mockMvc.perform(get(ME_URL).cookie(accessCookie(jwtTokenProvider.createAccessToken(7L))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(7))
				.andExpect(jsonPath("$.data.nickname").value("모여"))
				.andExpect(jsonPath("$.data.profileImageUrl").value("https://img.kakao.com/a.png"))
				.andExpect(jsonPath("$.data.kakaoId").doesNotExist())
				.andExpect(jsonPath("$.data.email").doesNotExist());
	}

	@Test
	@DisplayName("토큰이 없으면 401 UNAUTHORIZED JSON을 준다")
	void noToken() throws Exception {
		mockMvc.perform(get(ME_URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.name()));
	}

	@Test
	@DisplayName("만료된 토큰이면 401 EXPIRED_TOKEN JSON을 준다")
	void expiredToken() throws Exception {
		LocalDateTime past = LocalDateTime.now(TestClocks.SEOUL).minusDays(1);
		String token = new JwtTokenProvider(jwtProperties, TestClocks.fixed(past)).createAccessToken(7L);

		mockMvc.perform(get(ME_URL).cookie(accessCookie(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.EXPIRED_TOKEN.name()));
	}

	@Test
	@DisplayName("토큰은 유효한데 회원이 없으면 404 MEMBER_NOT_FOUND JSON을 준다")
	void memberNotFound() throws Exception {
		given(memberService.getMe(7L)).willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

		mockMvc.perform(get(ME_URL).cookie(accessCookie(jwtTokenProvider.createAccessToken(7L))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.MEMBER_NOT_FOUND.name()));
	}

	private static Cookie accessCookie(String token) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, token);
	}
}
