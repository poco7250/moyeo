package com.poco7250.moyeo.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.poco7250.moyeo.group.dto.GroupCreateRequest;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.group.dto.GroupResponse;
import com.poco7250.moyeo.group.dto.InviteLinkResponse;
import com.poco7250.moyeo.group.service.GroupService;
import com.poco7250.moyeo.group.service.InviteLinkService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GroupController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = GroupController.class)
@Import(SecurityTestConfig.class)
class GroupControllerTest {

	private static final String TOKEN = "t".repeat(43);
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 12, 0, 0);
	private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusDays(7);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	GroupService groupService;

	@MockitoBean
	InviteLinkService inviteLinkService;

	@Test
	@DisplayName("그룹 생성에 성공하면 201과 그룹·초대 링크를 공통 응답으로 준다")
	void create() throws Exception {
		given(groupService.create(eq(7L), any(GroupCreateRequest.class))).willReturn(new GroupCreateResponse(
				new GroupResponse(1L, "대학 동기", CREATED_AT),
				new InviteLinkResponse(TOKEN, "/invites/" + TOKEN, EXPIRES_AT)));

		mockMvc.perform(post("/api/groups").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"대학 동기\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.group.id").value(1))
				.andExpect(jsonPath("$.data.group.name").value("대학 동기"))
				.andExpect(jsonPath("$.data.inviteLink.token").value(TOKEN))
				.andExpect(jsonPath("$.data.inviteLink.url").value("/invites/" + TOKEN))
				.andExpect(jsonPath("$.data.inviteLink.expiresAt").exists())
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
	@DisplayName("그룹 이름이 없거나 공백이면 400 INVALID_INPUT과 name 필드 에러를 준다")
	void createWithoutName(String body) throws Exception {
		mockMvc.perform(post("/api/groups").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
		verifyNoInteractions(groupService);
	}

	@Test
	@DisplayName("그룹 이름이 50자를 넘으면 400이다")
	void createWithTooLongName() throws Exception {
		mockMvc.perform(post("/api/groups").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + "가".repeat(51) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
	}

	@Test
	@DisplayName("로그인하지 않으면 그룹을 만들 수 없다(401)")
	void createWithoutLogin() throws Exception {
		mockMvc.perform(post("/api/groups")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"대학 동기\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.name()));
	}

	@Test
	@DisplayName("내 그룹 목록을 준다")
	void myGroups() throws Exception {
		given(groupService.getMyGroups(7L)).willReturn(List.of(new GroupResponse(1L, "대학 동기", CREATED_AT)));

		mockMvc.perform(get("/api/groups").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(1))
				.andExpect(jsonPath("$.data[0].name").value("대학 동기"));
	}

	@Test
	@DisplayName("초대 링크를 재발급하면 201과 새 링크를 준다")
	void reissue() throws Exception {
		given(inviteLinkService.reissue(1L, 7L))
				.willReturn(new InviteLinkResponse(TOKEN, "/invites/" + TOKEN, EXPIRES_AT));

		mockMvc.perform(post("/api/groups/1/invite-links").cookie(accessCookie(7L)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.token").value(TOKEN));
	}

	@Test
	@DisplayName("비멤버가 초대 링크를 재발급하면 403 NOT_GROUP_MEMBER")
	void reissueByNonMember() throws Exception {
		given(inviteLinkService.reissue(1L, 8L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(post("/api/groups/1/invite-links").cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
