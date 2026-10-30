package com.poco7250.moyeo.global.config;

import com.poco7250.moyeo.auth.security.JwtAuthenticationFilter;
import com.poco7250.moyeo.auth.security.RestAuthenticationEntryPoint;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정.
 * 세션 없이 HttpOnly 쿠키(access_token)의 JWT로 인증하는 SPA용 REST API 기준이다.
 * 공개 경로 외에는 인증이 필요하고, 미인증이면 RestAuthenticationEntryPoint가 401 JSON을 준다.
 */
@Configuration
public class SecurityConfig {

	/**
	 * 무상태 필터 체인을 만든다.
	 * CSRF는 끈다: 인증 쿠키를 SameSite=Lax로 내려 다른 사이트의 POST·PUT·DELETE에는 쿠키가 실리지 않고,
	 * 상태를 바꾸는 GET 엔드포인트는 만들지 않는 규칙으로 남은 경로를 막는다.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
			RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
		return http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(SecurityConfig::authorizeRequests)
				.build();
	}

	/**
	 * 로그인 전에 접근해야 하는 경로만 열고 나머지는 인증을 요구한다.
	 */
	private static void authorizeRequests(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
		auth
				// 컨트롤러 예외가 /error로 포워드될 때 401로 바뀌지 않게 한다
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/kakao/login", "/api/auth/kakao/callback").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
				// 초대 링크 미리보기는 비로그인도 본다. OG 메타 HTML 경로는 Task 019에서 추가한다
				.requestMatchers(HttpMethod.GET, "/api/invites/*").permitAll()
				.anyRequest().authenticated();
	}
}
