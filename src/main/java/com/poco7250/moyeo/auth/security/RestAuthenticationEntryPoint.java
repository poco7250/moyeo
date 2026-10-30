package com.poco7250.moyeo.auth.security;

import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증이 필요한 경로에 미인증 요청이 오면 공통 응답 형식의 401 JSON을 준다.
 * 필터가 남긴 사유(EXPIRED_TOKEN 등)가 있으면 그 코드로, 없으면 UNAUTHORIZED로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	/**
	 * 에러 코드를 골라 JSON 응답을 쓴다.
	 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		ErrorCode errorCode = resolveErrorCode(request);
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode));
	}

	private ErrorCode resolveErrorCode(HttpServletRequest request) {
		Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
		return attribute instanceof ErrorCode errorCode ? errorCode : ErrorCode.UNAUTHORIZED;
	}
}
