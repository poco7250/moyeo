package com.poco7250.moyeo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 * 메인 클래스에 붙이면 @WebMvcTest 같은 슬라이스 테스트에서 JPA 빈을 찾다가 실패하므로 별도 설정으로 분리한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
