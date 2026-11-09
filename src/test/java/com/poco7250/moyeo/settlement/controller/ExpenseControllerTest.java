package com.poco7250.moyeo.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareAdjustRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseShareResponse;
import com.poco7250.moyeo.settlement.service.ExpenseService;
import com.poco7250.moyeo.settlement.service.ExpenseShareService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ExpenseController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = ExpenseController.class)
@Import(SecurityTestConfig.class)
class ExpenseControllerTest {

	private static final String VALID_BODY = "{\"amount\":100000,\"payerId\":8,\"memo\":\"볼링\"}";
	private static final ExpenseResponse RESPONSE = new ExpenseResponse(
			30L, 20L, 8L, 100_000L, "볼링", List.of(new ExpenseShareResponse(9L, null, null, true)));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	ExpenseService expenseService;

	@MockitoBean
	ExpenseShareService expenseShareService;

	@Test
	@DisplayName("지출을 등록하면 201과 공통 응답을 준다")
	void create() throws Exception {
		given(expenseService.create(eq(20L), eq(8L), any(ExpenseRequest.class))).willReturn(RESPONSE);

		mockMvc.perform(json(post("/api/rounds/20/expenses"), VALID_BODY).cookie(accessCookie()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(30))
				.andExpect(jsonPath("$.data.roundId").value(20))
				.andExpect(jsonPath("$.data.payerId").value(8))
				.andExpect(jsonPath("$.data.amount").value(100_000))
				.andExpect(jsonPath("$.data.memo").value("볼링"))
				.andExpect(jsonPath("$.data.shares[0].memberId").value(9))
				.andExpect(jsonPath("$.data.shares[0].excluded").value(true));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1", "100000000"})
	@DisplayName("금액 경계값 1원과 1억 원은 등록된다")
	void acceptsBoundaryAmount(String amount) throws Exception {
		given(expenseService.create(eq(20L), eq(8L), any(ExpenseRequest.class))).willReturn(RESPONSE);

		mockMvc.perform(json(post("/api/rounds/20/expenses"), "{\"amount\":" + amount + ",\"payerId\":8}")
						.cookie(accessCookie()))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "1.5", "1.0", "100000001", "\"abc\""})
	@DisplayName("금액이 0 이하이거나 정수가 아니거나 1억 원을 넘으면 400 INVALID_INPUT")
	void rejectsInvalidAmount(String amount) throws Exception {
		mockMvc.perform(json(post("/api/rounds/20/expenses"), "{\"amount\":" + amount + ",\"payerId\":8}")
						.cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(expenseService);
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
			"{\"payerId\":8}|amount",
			"{\"amount\":1000}|payerId"})
	@DisplayName("필수 값이 빠지면 400 필드 에러")
	void missingRequired(String body, String field) throws Exception {
		mockMvc.perform(json(post("/api/rounds/20/expenses"), body).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value(field));
		verifyNoInteractions(expenseService);
	}

	@Test
	@DisplayName("메모가 100자면 통과하고 101자면 400 필드 에러")
	void memoLength() throws Exception {
		given(expenseService.create(eq(20L), eq(8L), any(ExpenseRequest.class))).willReturn(RESPONSE);
		String template = "{\"amount\":1000,\"payerId\":8,\"memo\":\"%s\"}";

		mockMvc.perform(json(post("/api/rounds/20/expenses"), template.formatted("가".repeat(100)))
						.cookie(accessCookie()))
				.andExpect(status().isCreated());
		mockMvc.perform(json(post("/api/rounds/20/expenses"), template.formatted("가".repeat(101)))
						.cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("memo"));
	}

	@Test
	@DisplayName("지출 수정도 같은 검증을 거치고 성공하면 수정된 지출을 준다")
	void update() throws Exception {
		given(expenseService.update(eq(30L), eq(8L), any(ExpenseRequest.class))).willReturn(RESPONSE);

		mockMvc.perform(json(put("/api/expenses/30"), VALID_BODY).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(30));
		mockMvc.perform(json(put("/api/expenses/30"), "{\"amount\":1.5,\"payerId\":8}").cookie(accessCookie()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("없는 지출 수정은 404 EXPENSE_NOT_FOUND")
	void updateNotFound() throws Exception {
		given(expenseService.update(eq(30L), eq(8L), any(ExpenseRequest.class)))
				.willThrow(new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));

		mockMvc.perform(json(put("/api/expenses/30"), VALID_BODY).cookie(accessCookie()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.EXPENSE_NOT_FOUND.name()));
	}

	@Test
	@DisplayName("지출을 삭제하면 200과 빈 공통 응답을 준다")
	void deleteExpense() throws Exception {
		mockMvc.perform(delete("/api/expenses/30").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		verify(expenseService).delete(30L, 8L);
	}

	@Test
	@DisplayName("비멤버의 지출 삭제는 403 NOT_GROUP_MEMBER")
	void deleteByNonMember() throws Exception {
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER)).given(expenseService).delete(30L, 8L);

		mockMvc.perform(delete("/api/expenses/30").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	@Test
	@DisplayName("조정 항목은 제외·고정 금액·가중치를 각각 받고 excluded를 생략하면 제외가 아니다")
	void adjustShares() throws Exception {
		given(expenseShareService.adjust(eq(30L), eq(8L), any(ExpenseShareAdjustRequest.class))).willReturn(RESPONSE);

		mockMvc.perform(json(put("/api/expenses/30/shares"),
						"{\"shares\":[{\"memberId\":1,\"excluded\":true},{\"memberId\":2,\"amount\":5000},"
								+ "{\"memberId\":3,\"weight\":100}]}")
						.cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(30));

		ArgumentCaptor<ExpenseShareAdjustRequest> captor = ArgumentCaptor.forClass(ExpenseShareAdjustRequest.class);
		verify(expenseShareService).adjust(eq(30L), eq(8L), captor.capture());
		assertThat(captor.getValue().shares().getFirst().excludedOrFalse()).isTrue();
		assertThat(captor.getValue().shares().get(1).excludedOrFalse()).isFalse();
	}

	@Test
	@DisplayName("빈 조정 목록은 조정 전체 해제로 받는다")
	void adjustEmpty() throws Exception {
		given(expenseShareService.adjust(eq(30L), eq(8L), any(ExpenseShareAdjustRequest.class))).willReturn(RESPONSE);

		mockMvc.perform(json(put("/api/expenses/30/shares"), "{\"shares\":[]}").cookie(accessCookie()))
				.andExpect(status().isOk());
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
			"{\"memberId\":1}|조정 없음",
			"{\"memberId\":1,\"excluded\":false}|제외 false만",
			"{\"memberId\":1,\"excluded\":true,\"amount\":100}|제외+고정",
			"{\"memberId\":1,\"excluded\":true,\"weight\":2}|제외+가중치",
			"{\"memberId\":1,\"amount\":100,\"weight\":2}|고정+가중치"})
	@DisplayName("조정 종류가 정확히 하나가 아니면 400 singleAdjustment 필드 에러")
	void invalidAdjustmentKinds(String item, String caseName) throws Exception {
		mockMvc.perform(json(put("/api/expenses/30/shares"), "{\"shares\":[" + item + "]}").cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("shares[0].singleAdjustment"));
		verifyNoInteractions(expenseShareService);
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
			"{\"memberId\":1,\"weight\":0}|shares[0].weight",
			"{\"memberId\":1,\"weight\":101}|shares[0].weight",
			"{\"memberId\":1,\"amount\":0}|shares[0].amount",
			"{\"memberId\":1,\"amount\":100000001}|shares[0].amount",
			"{\"weight\":1}|shares[0].memberId"})
	@DisplayName("조정 값이 범위를 벗어나거나 회원이 없으면 400 필드 에러")
	void invalidAdjustmentValues(String item, String field) throws Exception {
		mockMvc.perform(json(put("/api/expenses/30/shares"), "{\"shares\":[" + item + "]}").cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value(field));
		verifyNoInteractions(expenseShareService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"memberId\":1,\"amount\":1.5}", "{\"memberId\":1,\"weight\":1.5}"})
	@DisplayName("고정 금액·가중치가 정수가 아니면 400 INVALID_INPUT")
	void nonIntegerAdjustment(String item) throws Exception {
		mockMvc.perform(json(put("/api/expenses/30/shares"), "{\"shares\":[" + item + "]}").cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(expenseShareService);
	}

	@Test
	@DisplayName("같은 회원을 두 번 조정하면 400 memberIdsUnique 필드 에러")
	void duplicateMembers() throws Exception {
		mockMvc.perform(json(put("/api/expenses/30/shares"),
						"{\"shares\":[{\"memberId\":1,\"weight\":1},{\"memberId\":1,\"excluded\":true}]}")
						.cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("memberIdsUnique"));
		verifyNoInteractions(expenseShareService);
	}

	@Test
	@DisplayName("조정 목록이 없으면 400 필드 에러")
	void missingShares() throws Exception {
		mockMvc.perform(json(put("/api/expenses/30/shares"), "{}").cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("shares"));
		verifyNoInteractions(expenseShareService);
	}

	@Test
	@DisplayName("모임 지출 목록을 공통 응답으로 준다")
	void list() throws Exception {
		given(expenseService.list(10L, 8L)).willReturn(List.of(RESPONSE));

		mockMvc.perform(get("/api/meetings/10/expenses").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value(30))
				.andExpect(jsonPath("$.data[0].amount").value(100_000));
	}

	@Test
	@DisplayName("비멤버의 지출 목록 조회는 403 NOT_GROUP_MEMBER")
	void listByNonMember() throws Exception {
		given(expenseService.list(10L, 8L)).willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		mockMvc.perform(get("/api/meetings/10/expenses").cookie(accessCookie()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_GROUP_MEMBER.name()));
	}

	@Test
	@DisplayName("비로그인 요청은 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(json(post("/api/rounds/20/expenses"), VALID_BODY))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/meetings/10/expenses"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(expenseService);
	}

	private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
