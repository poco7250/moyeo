package com.poco7250.moyeo.auth.security;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * access_token 쿠키의 JWT로 요청을 인증한다. principal은 회원 ID(Long)다.
 * 토큰이 잘못되면 인증 없이 다음 필터로 넘기고, 공개 경로가 아니면 엔트리포인트가 401을 준다.
 * 빈으로 등록하면 서블릿 필터로도 한 번 더 등록되므로 SecurityConfig에서 직접 생성한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	/** 토큰 검증 실패 사유(ErrorCode)를 엔트리포인트에 넘기는 요청 attribute 이름 */
	public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";

	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * 쿠키에 토큰이 있으면 검증해 SecurityContext에 인증을 넣는다.
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Cookie cookie = WebUtils.getCookie(request, TokenCookieFactory.ACCESS_TOKEN_COOKIE);
		if (cookie != null && StringUtils.hasText(cookie.getValue())) {
			authenticate(request, cookie.getValue());
		}
		chain.doFilter(request, response);
	}

	/**
	 * 토큰을 검증한다. 실패하면 사유만 attribute에 남긴다(토큰 원문은 남기지 않음).
	 */
	private void authenticate(HttpServletRequest request, String token) {
		try {
			Long memberId = jwtTokenProvider.parseMemberId(token);
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
			SecurityContextHolder.setContext(context);
		} catch (BusinessException e) {
			request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
		}
	}
}
