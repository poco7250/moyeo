package com.poco7250.moyeo.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MemberService memberService;

	@Test
	void 회원가입_폼을_보여준다() throws Exception {
		mockMvc.perform(get("/members/join"))
				.andExpect(status().isOk())
				.andExpect(view().name("member/joinForm"))
				.andExpect(model().attributeExists("joinRequest"));
	}

	@Test
	void 회원가입에_성공하면_완료_페이지로_리다이렉트한다() throws Exception {
		given(memberService.join(any())).willReturn(new MemberResponse(1L, "user@moyeo.com", "모여"));

		mockMvc.perform(validJoinPost())
				.andExpect(redirectedUrl("/members/join/complete"))
				.andExpect(flash().attribute("nickname", "모여"));
	}

	@Test
	void 입력값이_잘못되면_폼을_다시_보여준다() throws Exception {
		mockMvc.perform(post("/members/join")
						.param("email", "not-email")
						.param("password", "short")
						.param("passwordConfirm", "different")
						.param("nickname", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("member/joinForm"))
				.andExpect(model().attributeHasFieldErrors("joinRequest",
						"email", "password", "passwordConfirm", "nickname"));
		verifyNoInteractions(memberService);
	}

	@Test
	void 중복_회원이면_폼에_에러를_보여준다() throws Exception {
		given(memberService.join(any())).willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

		mockMvc.perform(validJoinPost())
				.andExpect(status().isOk())
				.andExpect(view().name("member/joinForm"))
				.andExpect(model().attributeHasErrors("joinRequest"));
	}

	private MockHttpServletRequestBuilder validJoinPost() {
		return post("/members/join")
				.param("email", "user@moyeo.com")
				.param("password", "password123")
				.param("passwordConfirm", "password123")
				.param("nickname", "모여");
	}
}
