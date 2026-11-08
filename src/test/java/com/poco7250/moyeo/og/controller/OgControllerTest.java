package com.poco7250.moyeo.og.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.og.dto.OgMeta;
import com.poco7250.moyeo.og.service.OgService;
import com.poco7250.moyeo.place.client.KakaoLocalClient;
import com.poco7250.moyeo.support.SecurityTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OgController 슬라이스 테스트. 실제 시큐리티 체인과 Thymeleaf로 렌더링하고, 서비스는 목이다.
 */
@WebMvcTest(controllers = OgController.class)
@Import(SecurityTestConfig.class)
class OgControllerTest {

	private static final OgMeta META = new OgMeta("연말 모임", "토요일 모임 · 강남 식당 · 5/8명 참석",
			"https://api.moyeo.example.com/share/meetings/t", "/meetings/10");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OgService ogService;

	@MockitoBean
	KakaoLocalClient kakaoLocalClient;

	@Test
	@DisplayName("로그인 없이 모임 공유 링크를 열면 OG 메타와 SPA 이동이 담긴 HTML을 준다")
	void meeting() throws Exception {
		given(ogService.meetingMeta("t")).willReturn(META);

		mockMvc.perform(get("/share/meetings/t"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("property=\"og:title\" content=\"연말 모임\"")))
				.andExpect(content().string(containsString(
						"property=\"og:description\" content=\"토요일 모임 · 강남 식당 · 5/8명 참석\"")))
				.andExpect(content().string(containsString(
						"property=\"og:url\" content=\"https://api.moyeo.example.com/share/meetings/t\"")))
				.andExpect(content().string(containsString("href=\"/meetings/10\"")))
				.andExpect(content().string(containsString("location.replace(")))
				.andExpect(content().string(not(containsString("http-equiv"))));
		verifyNoInteractions(kakaoLocalClient);
	}

	@Test
	@DisplayName("초대 공유 링크도 로그인 없이 OG 메타를 준다")
	void invite() throws Exception {
		given(ogService.inviteMeta("t")).willReturn(new OgMeta("대학 동기", "대학 동기 그룹에 초대받았어요 · 멤버 4명",
				"https://api.moyeo.example.com/share/invites/t", "/invites/t"));

		mockMvc.perform(get("/share/invites/t"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("property=\"og:title\" content=\"대학 동기\"")));
	}

	@Test
	@DisplayName("사용자가 입력한 제목·장소의 HTML 특수문자는 이스케이프한다")
	void escapesUserInput() throws Exception {
		given(ogService.meetingMeta("t")).willReturn(new OgMeta("<script>\"제목\"</script>", "<b>설명</b>",
				"https://api.moyeo.example.com/share/meetings/t", "/meetings/10"));

		mockMvc.perform(get("/share/meetings/t"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("<script>\"제목\""))))
				.andExpect(content().string(not(containsString("<b>설명"))))
				.andExpect(content().string(containsString("&lt;b&gt;설명")));
	}

	@Test
	@DisplayName("없는 모임 토큰은 404 안내 HTML을 준다")
	void meetingNotFound() throws Exception {
		given(ogService.meetingMeta("x")).willThrow(new BusinessException(ErrorCode.MEETING_NOT_FOUND));

		mockMvc.perform(get("/share/meetings/x"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("찾을 수 없는 링크")))
				.andExpect(content().string(not(containsString("location.replace"))));
	}

	@Test
	@DisplayName("만료된 초대 토큰은 410 안내 HTML을 준다")
	void inviteExpired() throws Exception {
		given(ogService.inviteMeta("x")).willThrow(new BusinessException(ErrorCode.INVITE_LINK_EXPIRED));

		mockMvc.perform(get("/share/invites/x"))
				.andExpect(status().isGone())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
	}

	@Test
	@DisplayName("공개 목록에 없는 /share 하위 경로는 로그인이 필요하다")
	void otherPathsRequireLogin() throws Exception {
		mockMvc.perform(get("/share/meetings"))
				.andExpect(status().isUnauthorized());
	}
}
