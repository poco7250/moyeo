package com.poco7250.moyeo.global.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.member.controller.MemberController;
import com.poco7250.moyeo.member.service.MemberService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 정적 SPA 서빙(Task 068) 슬라이스 테스트. 실제 시큐리티 체인으로
 * 화면 경로 forward·정적 파일 공개와, 그래도 API는 계속 인증이 필요한지 본다.
 */
@WebMvcTest(controllers = MemberController.class)
@Import({SecurityTestConfig.class, SpaForwardConfig.class})
class SpaForwardTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MemberService memberService;

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"/", "/login", "/invites/abc", "/groups/1", "/groups/1/members", "/meetings/7",
			"/meetings/7/settlement", "/groups", "/groups/1/hall-of-fame", "/settings"})
	@DisplayName("비로그인으로 화면 경로를 열면 index.html로 forward 된다")
	void screenPathForwardsToIndex(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl(SpaRoutes.INDEX));
	}

	@Test
	@DisplayName("비로그인도 SPA 진입 파일과 JS·CSS를 받는다")
	void staticFilesArePublic() throws Exception {
		mockMvc.perform(get(SpaRoutes.INDEX))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<main id=\"app\"")));
		mockMvc.perform(get("/js/app.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("startRouter")));
		mockMvc.perform(get("/css/app.css"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("화면 경로를 열어도 API는 그대로 인증이 필요하다(401 JSON)")
	void apiStillRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/members/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("화면 경로 공개는 GET만이다(같은 경로 POST는 인증 필요)")
	void screenPathOnlyGetIsPublic() throws Exception {
		mockMvc.perform(post("/meetings/7"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("목록에 없는 경로는 forward 하지 않는다")
	void unknownPathIsNotForwarded() throws Exception {
		mockMvc.perform(get("/unknown"))
				.andExpect(status().isUnauthorized());
	}
}
