package com.poco7250.moyeo.penalty.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
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
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleRequest;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleResponse;
import com.poco7250.moyeo.penalty.service.PenaltyConditionService;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * PenaltyController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = PenaltyController.class)
@Import(SecurityTestConfig.class)
class PenaltyControllerTest {

	private static final Long HOST_ID = 4L;
	private static final Long GAME_ID = 300L;
	private static final String RULE_URL = "/api/games/300/penalty-rule";
	private static final String WAIVER_URL = "/api/games/300/penalty-waiver";
	private static final LocalDateTime SET_AT = LocalDateTime.of(2026, 12, 20, 22, 0);
	private static final PenaltyConditionResponse APPLIED = new PenaltyConditionResponse(GAME_ID,
			PenaltyStatus.APPLIED, new PenaltyRuleResponse(PenaltyMoneyType.RATIO, null, 30, 900L, HOST_ID, SET_AT),
			null, null, List.of(2L, 3L));
	private static final PenaltyConditionResponse ASSIGNED = new PenaltyConditionResponse(GAME_ID,
			PenaltyStatus.ASSIGNED, null, null, null, List.of(2L, 3L));
	private static final PenaltyConditionResponse WAIVED = new PenaltyConditionResponse(GAME_ID,
			PenaltyStatus.WAIVED, null, HOST_ID, SET_AT, List.of(2L, 3L));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	PenaltyConditionService penaltyConditionService;

	@Test
	@DisplayName("조건을 설정하면 적용된 규칙과 벌칙자를 돌려준다")
	void setRuleResponse() throws Exception {
		given(penaltyConditionService.setRule(eq(GAME_ID), eq(HOST_ID), any())).willReturn(APPLIED);

		setRule("{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 30}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("APPLIED"))
				.andExpect(jsonPath("$.data.rule.moneyType").value("RATIO"))
				.andExpect(jsonPath("$.data.rule.ratioPct").value(30))
				.andExpect(jsonPath("$.data.rule.targetExpenseId").value(900))
				.andExpect(jsonPath("$.data.rule.setBy").value(4))
				.andExpect(jsonPath("$.data.penaltyMemberIds[1]").value(3));
		verify(penaltyConditionService).setRule(GAME_ID, HOST_ID,
				new PenaltyRuleRequest(PenaltyMoneyType.RATIO, 900L, null, 30));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900, \"amount\": 1}",
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900, \"amount\": 100000000}",
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 1}",
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 100}",
			"{\"moneyType\": \"FULL\", \"targetExpenseId\": 900}"})
	@DisplayName("종류별 경계값은 통과한다")
	void validBoundaries(String body) throws Exception {
		given(penaltyConditionService.setRule(eq(GAME_ID), eq(HOST_ID), any())).willReturn(APPLIED);

		setRule(body).andExpect(status().isOk());
	}

	@Test
	@DisplayName("대상 지출이 없으면 400이다")
	void targetExpenseMissing() throws Exception {
		assertInvalid("{\"moneyType\": \"RATIO\", \"ratioPct\": 30}", "targetExpenseId");
	}

	@Test
	@DisplayName("금액 종류가 없으면 400이다")
	void moneyTypeMissing() throws Exception {
		assertInvalid("{\"targetExpenseId\": 900, \"ratioPct\": 30}", "moneyType");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 0}",
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 101}",
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900}",
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 30, \"amount\": 1000}",
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900, \"amount\": 0}",
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900, \"amount\": 100000001}",
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900}",
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900, \"amount\": 1000, \"ratioPct\": 30}",
			"{\"moneyType\": \"FULL\", \"targetExpenseId\": 900, \"amount\": 1000}",
			"{\"moneyType\": \"FULL\", \"targetExpenseId\": 900, \"ratioPct\": 100}"})
	@DisplayName("종류와 값이 맞지 않거나 범위를 벗어나면 400이다")
	void invalidValue(String body) throws Exception {
		assertInvalid(body, "valueValid");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"moneyType\": \"RATIO\", \"targetExpenseId\": 900, \"ratioPct\": 30.5}",
			"{\"moneyType\": \"FIXED_AMOUNT\", \"targetExpenseId\": 900, \"amount\": 1000.5}",
			"{\"moneyType\": \"HALF\", \"targetExpenseId\": 900}"})
	@DisplayName("비율·금액이 소수이거나 모르는 종류면 400이다")
	void unreadableBody(String body) throws Exception {
		setRule(body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		verifyNoInteractions(penaltyConditionService);
	}

	@Test
	@DisplayName("서비스가 던진 404·400·403·409를 에러 코드와 함께 돌려준다")
	void serviceErrors() throws Exception {
		given(penaltyConditionService.setRule(eq(GAME_ID), eq(HOST_ID), any()))
				.willThrow(new BusinessException(ErrorCode.EXPENSE_NOT_FOUND))
				.willThrow(new BusinessException(ErrorCode.EXPENSE_NOT_IN_MEETING))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST))
				.willThrow(new BusinessException(ErrorCode.PENALTY_NOT_CONFIGURABLE));
		String body = "{\"moneyType\": \"FULL\", \"targetExpenseId\": 900}";

		setRule(body).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_FOUND"));
		setRule(body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_IN_MEETING"));
		setRule(body).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NOT_MEETING_HOST"));
		setRule(body).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PENALTY_NOT_CONFIGURABLE"));
	}

	@Test
	@DisplayName("조건 해제는 규칙이 없는 ASSIGNED 상태를 돌려준다")
	void clearRuleResponse() throws Exception {
		given(penaltyConditionService.clearRule(GAME_ID, HOST_ID)).willReturn(ASSIGNED);

		mockMvc.perform(delete(RULE_URL).cookie(loginCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.data.rule").doesNotExist());
	}

	@Test
	@DisplayName("면제는 면제자와 면제 시각을 돌려주고, 면제 취소는 ASSIGNED 상태를 돌려준다")
	void waiveAndUnwaive() throws Exception {
		given(penaltyConditionService.waive(GAME_ID, HOST_ID)).willReturn(WAIVED);
		given(penaltyConditionService.unwaive(GAME_ID, HOST_ID)).willReturn(ASSIGNED);

		mockMvc.perform(post(WAIVER_URL).cookie(loginCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("WAIVED"))
				.andExpect(jsonPath("$.data.waivedBy").value(4))
				.andExpect(jsonPath("$.data.waivedAt").exists());
		mockMvc.perform(delete(WAIVER_URL).cookie(loginCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.data.waivedBy").doesNotExist());
	}

	@Test
	@DisplayName("로그인하지 않으면 전부 401이다")
	void unauthenticated() throws Exception {
		mockMvc.perform(put(RULE_URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"moneyType\": \"FULL\", \"targetExpenseId\": 900}")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(RULE_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(WAIVER_URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(WAIVER_URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(penaltyConditionService);
	}

	private void assertInvalid(String body, String field) throws Exception {
		setRule(body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem(field)));
		verifyNoInteractions(penaltyConditionService);
	}

	private ResultActions setRule(String body) throws Exception {
		return mockMvc.perform(put(RULE_URL).cookie(loginCookie())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Cookie loginCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(HOST_ID));
	}
}
