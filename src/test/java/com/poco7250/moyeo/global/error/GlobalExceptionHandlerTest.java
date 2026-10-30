package com.poco7250.moyeo.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.support.SecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 시큐리티 기본 체인 대신 실제 SecurityConfig를 쓰고, 인증 통과 상태(@WithMockUser)에서 예외 응답만 검증한다
@WebMvcTest(controllers = ExceptionTestController.class)
@Import(SecurityTestConfig.class)
@WithMockUser
class GlobalExceptionHandlerTest {

	private static final String BASE_URL = "/test/exceptions";

	@Autowired
	MockMvc mockMvc;

	@Test
	void 성공_응답은_success_true와_data를_담고_error는_null이다() throws Exception {
		mockMvc.perform(get(BASE_URL + "/ok"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("모여"))
				.andExpect(jsonPath("$.data.amount").value(1000))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 필수_필드가_빠지면_400_INVALID_INPUT과_필드_에러_목록을_준다() throws Exception {
		postJson("{\"amount\": 1000}")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("name")));
	}

	@Test
	void 금액이_정수가_아니면_400_INVALID_INPUT을_준다() throws Exception {
		postJson("{\"name\": \"모여\", \"amount\": \"abc\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 쿼리_파라미터_제약을_위반하면_400_INVALID_INPUT과_파라미터_에러를_준다() throws Exception {
		mockMvc.perform(get(BASE_URL + "/param").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("size")));
	}

	@Test
	void ConstraintViolationException이면_400_INVALID_INPUT과_필드_에러를_준다() throws Exception {
		mockMvc.perform(get(BASE_URL + "/constraint"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("name")));
	}

	@Test
	void 비즈니스_예외는_에러_코드의_상태와_코드로_응답한다() throws Exception {
		mockMvc.perform(get(BASE_URL + "/business"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
				.andExpect(jsonPath("$.error.message").value(ErrorCode.DUPLICATE_RESOURCE.getMessage()));
	}

	@Test
	void DB_무결성_위반이면_409_DUPLICATE_RESOURCE를_준다() throws Exception {
		mockMvc.perform(get(BASE_URL + "/integrity"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
	}

	@Test
	void 낙관적_락_충돌이면_409_CONCURRENT_MODIFICATION_JSON을_준다() throws Exception {
		mockMvc.perform(get(BASE_URL + "/lock"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
	}

	@Test
	void 처리되지_않은_예외는_500_INTERNAL_ERROR를_주고_내부_정보를_노출하지_않는다() throws Exception {
		String body = mockMvc.perform(get(BASE_URL + "/error"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
				.andReturn().getResponse().getContentAsString();

		assertThat(body)
				.doesNotContain(ExceptionTestController.SENSITIVE_MESSAGE)
				.doesNotContain("IllegalStateException")
				.doesNotContain("at com.");
	}

	private ResultActions postJson(String json) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/valid")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}
}
