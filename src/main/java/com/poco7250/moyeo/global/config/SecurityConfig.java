package com.poco7250.moyeo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 * 세션 없이 HttpOnly 쿠키의 JWT로 인증하는 SPA용 REST API 기준이다.
 * 인증 필터와 401 엔트리포인트는 Task 006에서 붙이고, 지금은 모든 요청을 허용한다.
 */
@Configuration
public class SecurityConfig {

	/**
	 * 무상태 필터 체인을 만든다.
	 * CSRF는 끈다: 인증 쿠키를 SameSite=Lax로 내려 다른 사이트의 POST·PUT·DELETE에는 쿠키가 실리지 않고,
	 * 상태를 바꾸는 GET 엔드포인트는 만들지 않는 규칙으로 남은 경로를 막는다.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.build();
	}
}
