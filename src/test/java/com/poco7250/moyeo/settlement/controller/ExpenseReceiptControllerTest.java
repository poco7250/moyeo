package com.poco7250.moyeo.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptResponse;
import com.poco7250.moyeo.settlement.service.ExpenseReceiptService;
import com.poco7250.moyeo.settlement.service.ReceiptFile;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

/**
 * ExpenseReceiptController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = ExpenseReceiptController.class)
@Import(SecurityTestConfig.class)
class ExpenseReceiptControllerTest {

	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
	private static final MockMultipartFile FILE = new MockMultipartFile("file", "r.jpg", "image/jpeg", JPEG);
	private static final ExpenseReceiptResponse RESPONSE = new ExpenseReceiptResponse(
			"image/jpeg", 4L, 8L, LocalDateTime.of(2026, 11, 15, 12, 0), "/api/expenses/30/receipt?v=0");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	ExpenseReceiptService expenseReceiptService;

	@Test
	@DisplayName("사진을 올리면 200과 사진 정보를 공통 응답으로 준다")
	void upload() throws Exception {
		given(expenseReceiptService.upload(eq(30L), eq(8L), any(MultipartFile.class))).willReturn(RESPONSE);

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/expenses/30/receipt").file(FILE).cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
				.andExpect(jsonPath("$.data.size").value(4))
				.andExpect(jsonPath("$.data.uploadedBy").value(8))
				.andExpect(jsonPath("$.data.url").value("/api/expenses/30/receipt?v=0"));
	}

	@Test
	@DisplayName("file 파트가 없으면 400 INVALID_INPUT, multipart가 아니면 415이고 서비스를 부르지 않는다")
	void missingFile() throws Exception {
		MockMultipartFile wrongName = new MockMultipartFile("image", "r.jpg", "image/jpeg", JPEG);

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/expenses/30/receipt").file(wrongName)
						.cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		mockMvc.perform(put("/api/expenses/30/receipt").contentType(MediaType.APPLICATION_JSON).content("{}")
						.cookie(accessCookie()))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name()));
		verifyNoInteractions(expenseReceiptService);
	}

	@Test
	@DisplayName("서비스의 형식 오류 400·크기 초과 413을 그대로 전달한다")
	void propagatesErrors() throws Exception {
		given(expenseReceiptService.upload(eq(30L), eq(8L), any(MultipartFile.class)))
				.willThrow(new BusinessException(ErrorCode.RECEIPT_INVALID_TYPE))
				.willThrow(new BusinessException(ErrorCode.RECEIPT_TOO_LARGE));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/expenses/30/receipt").file(FILE).cookie(accessCookie()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.RECEIPT_INVALID_TYPE.name()));
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/expenses/30/receipt").file(FILE).cookie(accessCookie()))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.RECEIPT_TOO_LARGE.name()));
	}

	@Test
	@DisplayName("사진을 지우면 200, 사진이 없으면 404 RECEIPT_NOT_FOUND")
	void deleteReceipt() throws Exception {
		mockMvc.perform(delete("/api/expenses/30/receipt").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		verify(expenseReceiptService).delete(30L, 8L);

		willThrow(new BusinessException(ErrorCode.RECEIPT_NOT_FOUND)).given(expenseReceiptService).delete(31L, 8L);
		mockMvc.perform(delete("/api/expenses/31/receipt").cookie(accessCookie()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.RECEIPT_NOT_FOUND.name()));
	}

	@Test
	@DisplayName("사진 조회는 이미지 바이트와 형식·캐시·nosniff·inline 헤더를 준다")
	void download() throws Exception {
		given(expenseReceiptService.load(30L, 8L))
				.willReturn(new ReceiptFile(new ByteArrayResource(JPEG), "image/jpeg", 4L, "abc.jpg"));

		mockMvc.perform(get("/api/expenses/30/receipt").cookie(accessCookie()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"))
				.andExpect(content().bytes(JPEG))
				.andExpect(header().string("Cache-Control", "max-age=86400, private"))
				.andExpect(header().string("ETag", "\"abc.jpg\""))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Content-Disposition", "inline"));
	}

	@Test
	@DisplayName("비로그인 요청은 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/expenses/30/receipt").file(FILE))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/expenses/30/receipt"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/expenses/30/receipt"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(expenseReceiptService);
	}

	private Cookie accessCookie() {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(8L));
	}
}
