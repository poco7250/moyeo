package com.poco7250.moyeo.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 의존 로직(마감, 제한 시간 등)이 주입받아 쓰는 Clock 설정.
 * 테스트에서는 고정 Clock으로 바꿔 시간을 통제한다.
 */
@Configuration
public class ClockConfig {

	/**
	 * 서울 시간대 기준 시스템 Clock을 빈으로 등록한다.
	 */
	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
