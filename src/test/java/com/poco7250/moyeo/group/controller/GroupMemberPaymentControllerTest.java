package com.poco7250.moyeo.group.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.dto.PaymentInfoRequest;
import com.poco7250.moyeo.group.dto.PaymentInfoResponse;
import com.poco7250.moyeo.group.service.GroupMemberPaymentService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GroupMemberPaymentController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = GroupMemberPaymentController.class)
@Import(SecurityTestConfig.class)
class GroupMemberPaymentControllerTest {

	private static final String URL = "/api/groups/5/members/me/payment";
	private static final PaymentInfoResponse RESPONSE = new PaymentInfoResponse(
			8L, "국민", "110123456789", "철수", "https://toss.me/abc", null);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	GroupMemberPaymentService paymentService;

	@Test
	@DisplayName("저장하면 정규화한 요청으로 서비스를 부르고 송금 정보를 공통 응답으로 준다")
	void replace() throws Exception {
		given(paymentService.replaceMine(eq(5L), eq(8L), any(PaymentInfoRequest.class))).willReturn(RESPONSE);

		mockMvc.perform(put(URL).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"bankName\":\"국민\",\"accountNumber\":\"110-123-456789\",\"accountHolder\":\"철수\","
								+ "\"tossLink\":\"https://toss.me/abc\",\"kakaoPayLink\":\"\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.memberId").value(8))
				.andExpect(jsonPath("$.data.accountNumber").value("110123456789"))
				.andExpect(jsonPath("$.data.tossLink").value("https://toss.me/abc"));
		verify(paymentService).replaceMine(5L, 8L,
				new PaymentInfoRequest("국민", "110-123-456789", "철수", "https://toss.me/abc", null));
	}

	@Test
	@DisplayName("계좌 일부만 보내거나 http 링크면 400 필드 에러이고 서비스를 부르지 않는다")
	void rejectsInvalid() throws Exception {
		mockMvc.perform(put(URL).cookie(accessCookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"bankName\":\"국민\",\"tossLink\":\"http://toss.me/abc\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()))
				.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("accountComplete")))
				.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("tossLink")));
		verifyNoInteractions(paymentService);
	}

	@Test
	@DisplayName("조회는 저장된 정보를, 없으면 data null을 준다")
	void getMine() throws Exception {
		given(paymentService.getMine(5L, 8L)).willReturn(RESPONSE).willReturn(null);

		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bankName").value("국민"));
		mockMvc.perform(get(URL).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("지우면 200, 저장된 정보가 없으면 404 PAYMENT_INFO_NOT_FOUND")
	void deleteMine() throws Exception {
		mockMvc.perform(delete(URL).cookie(accessCookie()))
				.andExpect(status().isOk());
		verify(paymentService).deleteMine(5L, 8L);

		willThrow(new BusinessException(ErrorCode.PAYMENT_INFO_NOT_FOUND)).given(paymentService).deleteMine(6L, 8L);
		mockMvc.perform(delete("/api/groups/6/members/me/payment").cookie(accessCookie()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.PAYMENT_INFO_NOT_FOUND.name()));
	}

	@Test
	@DisplayName("비로그인 요청은 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"tossLink\":\"https://a.b\"}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(paymentService);
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
