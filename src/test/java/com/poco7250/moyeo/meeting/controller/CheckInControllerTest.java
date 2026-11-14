package com.poco7250.moyeo.meeting.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.dto.AttendanceConfirmResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.CheckInResponse;
import com.poco7250.moyeo.meeting.service.CheckInService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CheckInController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = CheckInController.class)
@Import(SecurityTestConfig.class)
class CheckInControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	CheckInService checkInService;

	@Test
	@DisplayName("내 체크인은 요청자 본인을 대상으로 체크인한다")
	void checkInMine() throws Exception {
		given(checkInService.changeCheckIn(101L, 8L, 8L, true)).willReturn(new CheckInResponse(101L, 8L, true));

		mockMvc.perform(put("/api/rounds/101/check-ins/me").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.roundId").value(101))
				.andExpect(jsonPath("$.data.memberId").value(8))
				.andExpect(jsonPath("$.data.checkedIn").value(true));
	}

	@Test
	@DisplayName("내 체크인 취소는 checkedIn=false로 서비스를 부른다")
	void cancelMine() throws Exception {
		given(checkInService.changeCheckIn(101L, 8L, 8L, false)).willReturn(new CheckInResponse(101L, 8L, false));

		mockMvc.perform(delete("/api/rounds/101/check-ins/me").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.checkedIn").value(false));
	}

	@Test
	@DisplayName("대리 체크인·취소는 경로의 멤버 id를 대상으로 서비스를 부른다")
	void proxy() throws Exception {
		given(checkInService.changeCheckIn(101L, 8L, 9L, true)).willReturn(new CheckInResponse(101L, 9L, true));
		given(checkInService.changeCheckIn(101L, 8L, 9L, false)).willReturn(new CheckInResponse(101L, 9L, false));

		mockMvc.perform(put("/api/rounds/101/check-ins/9").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.memberId").value(9))
				.andExpect(jsonPath("$.data.checkedIn").value(true));
		mockMvc.perform(delete("/api/rounds/101/check-ins/9").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.checkedIn").value(false));
	}

	@Test
	@DisplayName("실제 참석자 확정은 참석·불참 목록과 확정 시각을 준다")
	void confirm() throws Exception {
		given(checkInService.confirm(101L, 8L)).willReturn(new AttendanceConfirmResponse(10L, 101L,
				LocalDateTime.of(2026, 12, 20, 20, 0), List.of(new BoardMember(8L, "철수", null)),
				List.of(new BoardMember(9L, "민수", "🍺")), 1));

		mockMvc.perform(post("/api/rounds/101/attendance/confirm").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetingId").value(10))
				.andExpect(jsonPath("$.data.confirmedAt").value("2026-12-20T20:00:00"))
				.andExpect(jsonPath("$.data.attend[0].nickname").value("철수"))
				.andExpect(jsonPath("$.data.absent[0].memberId").value(9))
				.andExpect(jsonPath("$.data.changedCount").value(1));
	}

	@Test
	@DisplayName("모임 당일 전이면 409 CHECK_IN_NOT_OPEN, 비호스트 확정은 403 NOT_MEETING_HOST를 그대로 전달한다")
	void errors() throws Exception {
		given(checkInService.changeCheckIn(101L, 8L, 8L, true))
				.willThrow(new BusinessException(ErrorCode.CHECK_IN_NOT_OPEN));
		given(checkInService.confirm(101L, 8L)).willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		mockMvc.perform(put("/api/rounds/101/check-ins/me").cookie(accessCookie(8L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.CHECK_IN_NOT_OPEN.name()));
		mockMvc.perform(post("/api/rounds/101/attendance/confirm").cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_MEETING_HOST.name()));
	}

	@Test
	@DisplayName("로그인하지 않으면 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(put("/api/rounds/101/check-ins/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/rounds/101/attendance/confirm")).andExpect(status().isUnauthorized());
		verifyNoInteractions(checkInService);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
