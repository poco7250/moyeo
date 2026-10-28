package com.poco7250.moyeo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 설정.
 * 투표 마감·꽝뽑기 제한 시간 같은 예약 작업(@Scheduled)에서 사용한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
