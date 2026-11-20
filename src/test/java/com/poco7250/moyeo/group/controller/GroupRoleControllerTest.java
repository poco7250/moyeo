package com.poco7250.moyeo.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.service.GroupRoleService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GroupRoleController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = GroupRoleController.class)
@Import(SecurityTestConfig.class)
class GroupRoleControllerTest {

	private static final String ROLE = "/api/groups/5/members/8/role";
	private static final String TRANSFER = "/api/groups/5/owner-transfer";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	GroupRoleService groupRoleService;

	@Test
	@DisplayName("역할을 바꾸면 바뀐 멤버를 공통 응답 래퍼로 준다")
	void changeRole() throws Exception {
		given(groupRoleService.changeRole(5L, 7L, 8L, GroupRole.TREASURER))
				.willReturn(new GroupMemberResponse(8L, "민수", null, GroupRole.TREASURER));

		mockMvc.perform(put(ROLE).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"TREASURER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.memberId").value(8))
				.andExpect(jsonPath("$.data.role").value("TREASURER"));
	}

	@Test
	@DisplayName("방장을 넘기면 이전 방장·새 방장 순으로 바뀐 두 멤버를 준다")
	void transferOwner() throws Exception {
		given(groupRoleService.transferOwner(5L, 7L, 8L)).willReturn(List.of(
				new GroupMemberResponse(7L, "철수", null, GroupRole.MEMBER),
				new GroupMemberResponse(8L, "민수", null, GroupRole.OWNER)));

		mockMvc.perform(post(TRANSFER).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"memberId\":8}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].memberId").value(7))
				.andExpect(jsonPath("$.data[0].role").value("MEMBER"))
				.andExpect(jsonPath("$.data[1].role").value("OWNER"));
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
			"{}|역할 누락",
			"{\"role\":null}|역할 null",
			"{\"role\":\"BOSS\"}|없는 역할"})
	@DisplayName("역할 요청이 잘못되면 400 INVALID_INPUT이고 서비스를 부르지 않는다")
	void invalidRole(String body, String caseName) throws Exception {
		mockMvc.perform(put(ROLE).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(groupRoleService);
	}

	@Test
	@DisplayName("양도 대상이 없으면 400 INVALID_INPUT")
	void invalidTransfer() throws Exception {
		mockMvc.perform(post(TRANSFER).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'memberId')]").exists());
		verifyNoInteractions(groupRoleService);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"NOT_GROUP_OWNER, 403", "NOT_GROUP_MEMBER, 403", "GROUP_MEMBER_NOT_FOUND, 404",
			"GROUP_ROLE_INVALID_CHANGE, 409"})
	@DisplayName("역할 변경·양도 거절을 그대로 전달한다")
	void rejected(ErrorCode code, int httpStatus) throws Exception {
		given(groupRoleService.changeRole(anyLong(), anyLong(), anyLong(), any()))
				.willThrow(new BusinessException(code));
		given(groupRoleService.transferOwner(anyLong(), anyLong(), anyLong())).willThrow(new BusinessException(code));

		mockMvc.perform(put(ROLE).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"MEMBER\"}"))
				.andExpect(status().is(httpStatus))
				.andExpect(jsonPath("$.error.code").value(code.name()));
		mockMvc.perform(post(TRANSFER).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"memberId\":8}"))
				.andExpect(status().is(httpStatus))
				.andExpect(jsonPath("$.error.code").value(code.name()));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous() throws Exception {
		mockMvc.perform(put(ROLE).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(TRANSFER).contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":8}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(groupRoleService);
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(7L));
	}
}
