package com.poco7250.moyeo.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.dto.AttendanceBoardResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.dto.RoundBoard;
import com.poco7250.moyeo.meeting.service.AttendanceBoardService;
import com.poco7250.moyeo.meeting.service.AttendanceService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
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
 * AttendanceController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = AttendanceController.class)
@Import(SecurityTestConfig.class)
class AttendanceControllerTest {

	private static final String SHORTCUT_BODY = "{\"shortcut\":\"LATE_FROM_SECOND\"}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	AttendanceService attendanceService;

	@MockitoBean
	AttendanceBoardService attendanceBoardService;

	@Test
	@DisplayName("내 응답 저장에 성공하면 차수별 응답을 공통 응답으로 준다")
	void respondMine() throws Exception {
		given(attendanceService.respondMine(eq(10L), eq(8L), any(AttendanceRespondRequest.class)))
				.willReturn(new MemberAttendanceResponse(8L, List.of(
						new RoundAttendanceItem(101L, AttendanceStatus.ABSENT),
						new RoundAttendanceItem(102L, AttendanceStatus.ATTEND))));

		mockMvc.perform(putJson("/api/meetings/10/attendances/me", SHORTCUT_BODY).cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.memberId").value(8))
				.andExpect(jsonPath("$.data.rounds[0].roundId").value(101))
				.andExpect(jsonPath("$.data.rounds[0].status").value("ABSENT"))
				.andExpect(jsonPath("$.data.rounds[1].status").value("ATTEND"));
	}

	@Test
	@DisplayName("호스트 대리 수정은 경로의 멤버 id로 서비스를 부른다")
	void respondFor() throws Exception {
		given(attendanceService.respondFor(eq(10L), eq(7L), eq(8L), any(AttendanceRespondRequest.class)))
				.willReturn(new MemberAttendanceResponse(8L, List.of()));

		mockMvc.perform(putJson("/api/meetings/10/attendances/8", SHORTCUT_BODY).cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.memberId").value(8));
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
			"{}|둘 다 없음",
			"{\"shortcut\":\"FIRST_ONLY\",\"rounds\":[{\"roundId\":1,\"status\":\"ATTEND\"}]}|둘 다 있음",
			"{\"rounds\":[]}|빈 차수 목록"})
	@DisplayName("단축 옵션과 차수별 응답 중 정확히 하나가 아니면 400 answerSpecValid 필드 에러")
	void invalidAnswerSpec(String body, String caseName) throws Exception {
		mockMvc.perform(putJson("/api/meetings/10/attendances/me", body).cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("answerSpecValid"));
		verifyNoInteractions(attendanceService);
	}

	@Test
	@DisplayName("같은 차수를 두 번 보내면 400 roundIdsUnique 필드 에러")
	void duplicateRoundIds() throws Exception {
		mockMvc.perform(putJson("/api/meetings/10/attendances/me",
						"{\"rounds\":[{\"roundId\":1,\"status\":\"ATTEND\"},{\"roundId\":1,\"status\":\"ABSENT\"}]}")
						.cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("roundIdsUnique"));
		verifyNoInteractions(attendanceService);
	}

	@Test
	@DisplayName("차수 응답에 상태가 없으면 400 필드 에러")
	void missingStatus() throws Exception {
		mockMvc.perform(putJson("/api/meetings/10/attendances/me", "{\"rounds\":[{\"roundId\":1}]}")
						.cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("rounds[0].status"));
		verifyNoInteractions(attendanceService);
	}

	@Test
	@DisplayName("알 수 없는 단축 옵션은 400 INVALID_INPUT")
	void unknownShortcut() throws Exception {
		mockMvc.perform(putJson("/api/meetings/10/attendances/me", "{\"shortcut\":\"ALL\"}")
						.cookie(accessCookie(8L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
	}

	@Test
	@DisplayName("비로그인 응답 저장은 401")
	void unauthenticated() throws Exception {
		mockMvc.perform(putJson("/api/meetings/10/attendances/me", SHORTCUT_BODY))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(attendanceService);
	}

	@Test
	@DisplayName("마감이 지났으면 403 ATTENDANCE_DEADLINE_PASSED")
	void deadlinePassed() throws Exception {
		given(attendanceService.respondMine(eq(10L), eq(8L), any(AttendanceRespondRequest.class)))
				.willThrow(new BusinessException(ErrorCode.ATTENDANCE_DEADLINE_PASSED));

		mockMvc.perform(putJson("/api/meetings/10/attendances/me", SHORTCUT_BODY).cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.ATTENDANCE_DEADLINE_PASSED.name()));
	}

	@Test
	@DisplayName("호스트가 아닌 멤버의 대리 수정은 403 NOT_MEETING_HOST")
	void proxyByNonHost() throws Exception {
		given(attendanceService.respondFor(eq(10L), eq(8L), eq(9L), any(AttendanceRespondRequest.class)))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		mockMvc.perform(putJson("/api/meetings/10/attendances/9", SHORTCUT_BODY).cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_MEETING_HOST.name()));
	}

	@Test
	@DisplayName("현황판은 차수별 참석/불참/미정/미응답 목록과 마감 정보를 준다")
	void board() throws Exception {
		BoardMember ga = new BoardMember(1L, "가", "🍺");
		BoardMember na = new BoardMember(2L, "나", null);
		given(attendanceBoardService.getBoard(10L, 8L)).willReturn(new AttendanceBoardResponse(
				LocalDateTime.of(2026, 12, 19, 18, 0), false,
				List.of(new RoundBoard(101L, 1, "밥", List.of(ga), List.of(), List.of(), List.of(na)))));

		mockMvc.perform(get("/api/meetings/10/attendance-board").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.responseDeadline").value("2026-12-19T18:00:00"))
				.andExpect(jsonPath("$.data.closed").value(false))
				.andExpect(jsonPath("$.data.rounds[0].name").value("밥"))
				.andExpect(jsonPath("$.data.rounds[0].attend[0].nickname").value("가"))
				.andExpect(jsonPath("$.data.rounds[0].attend[0].emoji").value("🍺"))
				.andExpect(jsonPath("$.data.rounds[0].absent").isEmpty())
				.andExpect(jsonPath("$.data.rounds[0].noResponse[0].memberId").value(2));
	}

	@Test
	@DisplayName("비멤버의 현황판 조회는 403 NOT_GROUP_MEMBER")
	void boardByNonMember() throws Exception {
		given(attendanceBoardService.getBoard(10L, 8L))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/meetings/10/attendance-board").cookie(accessCookie(8L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putJson(
			String url, String body) {
		return put(url).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
