package com.poco7250.moyeo.meeting.controller;

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
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.dto.GroupMeetingsResponse;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.dto.MeetingSummaryResponse;
import com.poco7250.moyeo.meeting.dto.RoundResponse;
import com.poco7250.moyeo.meeting.service.MeetingService;
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
 * MeetingController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = MeetingController.class)
@Import(SecurityTestConfig.class)
class MeetingControllerTest {

	private static final LocalDateTime START_AT = LocalDateTime.of(2026, 12, 20, 18, 0);
	private static final String VALID_BODY =
			"{\"title\":\"연말 모임\",\"startAt\":\"2026-12-20T18:00:00\",\"memo\":\"메모\",\"template\":\"MEAL_BOWLING\"}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	MeetingService meetingService;

	@Test
	@DisplayName("모임 생성에 성공하면 201과 모임 상세 링크·차수를 공통 응답으로 준다")
	void create() throws Exception {
		given(meetingService.create(eq(1L), eq(7L), any(MeetingCreateRequest.class))).willReturn(detail());

		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(10))
				.andExpect(jsonPath("$.data.hostMemberId").value(7))
				.andExpect(jsonPath("$.data.host").value(true))
				.andExpect(jsonPath("$.data.url").value("/meetings/10"))
				.andExpect(jsonPath("$.data.rounds[0].seq").value(1))
				.andExpect(jsonPath("$.data.rounds[0].name").value("밥"))
				.andExpect(jsonPath("$.data.rounds[1].name").value("볼링"))
				.andExpect(jsonPath("$.data.rounds[1].placeStatus").value("UNDECIDED"))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("제목·날짜가 없으면 400 INVALID_INPUT과 title·startAt 필드 에러를 준다")
	void createWithoutTitleAndStartAt() throws Exception {
		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\" \",\"template\":\"MEAL_ONLY\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'title')]").exists())
				.andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'startAt')]").exists());
		verifyNoInteractions(meetingService);
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
			"|템플릿·차수 둘 다 없음",
			"\"template\":\"MEAL_ONLY\",\"rounds\":[\"밥\"]|둘 다 있음",
			"\"rounds\":[]|빈 차수 목록"})
	@DisplayName("차수 템플릿과 차수 목록 중 정확히 하나가 아니면 400 roundSpecValid 필드 에러")
	void createWithInvalidRoundSpec(String roundJson, String caseName) throws Exception {
		String extra = roundJson == null ? "" : "," + roundJson;
		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"연말 모임\",\"startAt\":\"2026-12-20T18:00:00\"" + extra + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("roundSpecValid"));
		verifyNoInteractions(meetingService);
	}

	@Test
	@DisplayName("차수가 6개면 400 rounds 필드 에러")
	void createWithTooManyRounds() throws Exception {
		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("rounds"));
	}

	@Test
	@DisplayName("차수 이름이 31자거나 공백이면 400")
	void createWithInvalidRoundName() throws Exception {
		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("[\"" + "가".repeat(31) + "\",\" \"]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
		verifyNoInteractions(meetingService);
	}

	@Test
	@DisplayName("알 수 없는 템플릿 이름이면 400 INVALID_INPUT")
	void createWithUnknownTemplate() throws Exception {
		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(7L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"연말 모임\",\"startAt\":\"2026-12-20T18:00:00\",\"template\":\"NOPE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
	}

	@Test
	@DisplayName("로그인하지 않으면 모임을 만들 수 없다(401)")
	void createWithoutLogin() throws Exception {
		mockMvc.perform(post("/api/groups/1/meetings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.name()));
	}

	@Test
	@DisplayName("비멤버가 모임을 만들면 403 NOT_GROUP_MEMBER")
	void createByNonMember() throws Exception {
		given(meetingService.create(eq(1L), eq(8L), any(MeetingCreateRequest.class)))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(post("/api/groups/1/meetings").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	@Test
	@DisplayName("모임 상세를 준다")
	void detailOk() throws Exception {
		given(meetingService.getDetail(10L, 7L)).willReturn(detail());

		mockMvc.perform(get("/api/meetings/10").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("연말 모임"))
				.andExpect(jsonPath("$.data.rounds.length()").value(2));
	}

	@Test
	@DisplayName("그룹 모임 목록을 다가오는/지난으로 나눠 준다")
	void groupMeetings() throws Exception {
		given(meetingService.getGroupMeetings(1L, 7L)).willReturn(new GroupMeetingsResponse(
				List.of(new MeetingSummaryResponse(10L, "연말 모임", START_AT, 7L)),
				List.of(new MeetingSummaryResponse(9L, "가을 모임", START_AT.minusMonths(2), 7L))));

		mockMvc.perform(get("/api/groups/1/meetings").cookie(accessCookie(7L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.upcoming[0].id").value(10))
				.andExpect(jsonPath("$.data.past[0].id").value(9));
	}

	private String body(String roundsJson) {
		return "{\"title\":\"연말 모임\",\"startAt\":\"2026-12-20T18:00:00\",\"rounds\":" + roundsJson + "}";
	}

	private MeetingDetailResponse detail() {
		return new MeetingDetailResponse(10L, 1L, "연말 모임", START_AT, "메모", null, 7L, true, "/meetings/10",
				List.of(new RoundResponse(100L, 1, "밥", PlaceStatus.UNDECIDED, null),
						new RoundResponse(101L, 2, "볼링", PlaceStatus.UNDECIDED, null)));
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
