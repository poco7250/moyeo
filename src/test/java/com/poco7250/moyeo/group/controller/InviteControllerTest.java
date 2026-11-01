package com.poco7250.moyeo.group.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import com.poco7250.moyeo.group.dto.InvitePreviewResponse;
import com.poco7250.moyeo.group.service.InviteJoinService;
import com.poco7250.moyeo.group.service.InviteLinkService;
import com.poco7250.moyeo.support.SecurityTestConfig;
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
 * InviteController 슬라이스 테스트. 미리보기는 공개 경로, 가입은 쿠키 인증이 필요하다.
 */
@WebMvcTest(controllers = InviteController.class)
@Import(SecurityTestConfig.class)
class InviteControllerTest {

	private static final String TOKEN = "t".repeat(43);
	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 8, 12, 0, 0);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	InviteLinkService inviteLinkService;

	@MockitoBean
	InviteJoinService inviteJoinService;

	@Test
	@DisplayName("비로그인도 초대 미리보기를 본다")
	void previewWithoutLogin() throws Exception {
		given(inviteLinkService.getPreview(TOKEN)).willReturn(new InvitePreviewResponse("대학 동기", 3L, EXPIRES_AT));

		mockMvc.perform(get("/api/invites/" + TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.groupName").value("대학 동기"))
				.andExpect(jsonPath("$.data.memberCount").value(3))
				.andExpect(jsonPath("$.data.expiresAt").exists())
				.andExpect(jsonPath("$.data.groupId").doesNotExist());
	}

	@Test
	@DisplayName("만료·폐기된 링크 미리보기는 410 INVITE_LINK_EXPIRED")
	void previewExpired() throws Exception {
		given(inviteLinkService.getPreview(TOKEN)).willThrow(new BusinessException(ErrorCode.INVITE_LINK_EXPIRED));

		mockMvc.perform(get("/api/invites/" + TOKEN))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVITE_LINK_EXPIRED.name()));
	}

	@Test
	@DisplayName("없는 링크 미리보기는 404 INVITE_LINK_NOT_FOUND")
	void previewNotFound() throws Exception {
		given(inviteLinkService.getPreview(TOKEN)).willThrow(new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));

		mockMvc.perform(get("/api/invites/" + TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVITE_LINK_NOT_FOUND.name()));
	}

	@Test
	@DisplayName("로그인하면 초대 링크로 가입하고 그룹 id를 받는다")
	void join() throws Exception {
		given(inviteJoinService.join(TOKEN, 7L)).willReturn(new InviteJoinResponse(1L, true));

		mockMvc.perform(post("/api/invites/" + TOKEN + "/join").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.groupId").value(1))
				.andExpect(jsonPath("$.data.joined").value(true));
	}

	@Test
	@DisplayName("로그인하지 않으면 가입할 수 없다(401)")
	void joinWithoutLogin() throws Exception {
		mockMvc.perform(post("/api/invites/" + TOKEN + "/join"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.name()));
		verifyNoInteractions(inviteJoinService);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
