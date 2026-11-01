package com.poco7250.moyeo.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.dto.GroupMemberUpdateRequest;
import com.poco7250.moyeo.group.service.GroupMemberService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
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
 * GroupMemberController 슬라이스 테스트.
 */
@WebMvcTest(controllers = GroupMemberController.class)
@Import(SecurityTestConfig.class)
class GroupMemberControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	GroupMemberService groupMemberService;

	@Test
	@DisplayName("그룹 멤버는 멤버 목록(닉네임·이모지)을 받는다")
	void members() throws Exception {
		given(groupMemberService.getMembers(1L, 7L)).willReturn(List.of(
				new GroupMemberResponse(7L, "모여", "🐶", GroupRole.OWNER),
				new GroupMemberResponse(8L, "친구", null, GroupRole.MEMBER)));

		mockMvc.perform(get("/api/groups/1/members").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].memberId").value(7))
				.andExpect(jsonPath("$.data[0].nickname").value("모여"))
				.andExpect(jsonPath("$.data[0].emoji").value("🐶"))
				.andExpect(jsonPath("$.data[0].role").value("OWNER"))
				.andExpect(jsonPath("$.data[1].nickname").value("친구"));
	}

	@Test
	@DisplayName("비멤버가 멤버 목록을 조회하면 403 NOT_GROUP_MEMBER")
	void membersByNonMember() throws Exception {
		given(groupMemberService.getMembers(1L, 9L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/groups/1/members").cookie(accessCookie(9L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	@Test
	@DisplayName("내 그룹 프로필을 바꾼다")
	void updateMe() throws Exception {
		given(groupMemberService.updateMyProfile(eq(1L), eq(7L), any(GroupMemberUpdateRequest.class)))
				.willReturn(new GroupMemberResponse(7L, "총무", "🐶", GroupRole.OWNER));

		mockMvc.perform(patch("/api/groups/1/members/me").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"총무\",\"emoji\":\"🐶\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("총무"))
				.andExpect(jsonPath("$.data.emoji").value("🐶"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"nickname\":\"\"}", "{\"nickname\":\"   \"}"})
	@DisplayName("닉네임이 없거나 공백이면 400 INVALID_INPUT과 nickname 필드 에러")
	void updateMeWithBlankNickname(String body) throws Exception {
		mockMvc.perform(patch("/api/groups/1/members/me").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
		verifyNoInteractions(groupMemberService);
	}

	@Test
	@DisplayName("닉네임 50자·이모지 16자를 넘으면 400")
	void updateMeWithTooLongValues() throws Exception {
		mockMvc.perform(patch("/api/groups/1/members/me").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"" + "가".repeat(51) + "\",\"emoji\":\"" + "a".repeat(17) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
		verifyNoInteractions(groupMemberService);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
