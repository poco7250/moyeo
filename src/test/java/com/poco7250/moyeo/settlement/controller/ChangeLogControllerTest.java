package com.poco7250.moyeo.settlement.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ChangeLogPageResponse;
import com.poco7250.moyeo.settlement.dto.ChangeLogResponse;
import com.poco7250.moyeo.settlement.service.ChangeLogQueryService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ChangeLogController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = ChangeLogController.class)
@Import(SecurityTestConfig.class)
class ChangeLogControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	ChangeLogQueryService changeLogQueryService;

	@Test
	@DisplayName("변경 로그는 기본 0페이지·50건이고, 전/후 값은 문자열이 아닌 JSON 객체로 내려간다")
	void list() throws Exception {
		given(changeLogQueryService.list(10L, 8L, 0, 50)).willReturn(new ChangeLogPageResponse(List.of(
				new ChangeLogResponse(1L, 8L, LocalDateTime.of(2026, 12, 20, 22, 0), ChangeLogEntityType.EXPENSE, 31L,
						ChangeType.UPDATE, "{\"amount\":20000,\"memo\":\"볼링\"}", "{\"amount\":30000,\"memo\":\"볼링\"}",
						SettlementStatus.REOPENED),
				new ChangeLogResponse(2L, null, LocalDateTime.of(2026, 12, 20, 22, 5), ChangeLogEntityType.EXPENSE,
						32L, ChangeType.CREATE, null, "{\"amount\":10000}", SettlementStatus.REOPENED)),
				0, 50, true));

		mockMvc.perform(get("/api/meetings/10/change-logs").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(50))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.items[0].actorId").value(8))
				.andExpect(jsonPath("$.data.items[0].changedAt").value("2026-12-20T22:00:00"))
				.andExpect(jsonPath("$.data.items[0].entityType").value("EXPENSE"))
				.andExpect(jsonPath("$.data.items[0].changeType").value("UPDATE"))
				.andExpect(jsonPath("$.data.items[0].before.amount").value(20_000))
				.andExpect(jsonPath("$.data.items[0].after.memo").value("볼링"))
				.andExpect(jsonPath("$.data.items[0].settlementStatusAfter").value("REOPENED"))
				.andExpect(jsonPath("$.data.items[1].actorId").isEmpty())
				.andExpect(jsonPath("$.data.items[1].before").isEmpty())
				.andExpect(jsonPath("$.data.items[1].after.amount").value(10_000));
	}

	@Test
	@DisplayName("page·size를 넘기면 그대로 서비스에 전달한다")
	void passesPaging() throws Exception {
		given(changeLogQueryService.list(10L, 8L, 2, 100)).willReturn(new ChangeLogPageResponse(List.of(), 2, 100, false));

		mockMvc.perform(get("/api/meetings/10/change-logs").param("page", "2").param("size", "100")
						.cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@ParameterizedTest
	@CsvSource({"-1,50", "0,0", "0,101"})
	@DisplayName("page가 음수이거나 size가 1~100 밖이면 400 INVALID_INPUT이고 서비스를 호출하지 않는다")
	void invalidPaging(String page, String size) throws Exception {
		mockMvc.perform(get("/api/meetings/10/change-logs").param("page", page).param("size", size)
						.cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(changeLogQueryService);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() throws Exception {
		given(changeLogQueryService.list(10L, 8L, 0, 50)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/meetings/10/change-logs").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(get("/api/meetings/10/change-logs"))
				.andExpect(status().isUnauthorized());
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
