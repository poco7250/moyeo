package com.poco7250.moyeo.support;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.security.RestAuthenticationEntryPoint;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.global.config.ClockConfig;
import com.poco7250.moyeo.global.config.SecurityConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * {@code @WebMvcTest}에서 실제 시큐리티 체인(JWT 쿠키 인증·401 JSON)을 쓰기 위한 설정.
 * 슬라이스는 {@code @Component}를 스캔하지 않으므로 필요한 빈을 직접 넣는다. JWT 키는 application-test.properties 값이다.
 */
@TestConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, JwtTokenProvider.class, ClockConfig.class})
public class SecurityTestConfig {
}
