package com.poco7250.moyeo.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행 설정. 웹 푸시 전송(@Async)이 요청 스레드를 붙잡지 않도록 전용 스레드 풀을 둔다.
 * STOMP 채널 실행기 빈이 여럿이라 기본 실행기 선택에 기대지 않고 이름으로 지정한다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

	/** 웹 푸시 전송 스레드 풀 빈 이름. {@code @Async(AsyncConfig.PUSH_EXECUTOR)}로 쓴다 */
	public static final String PUSH_EXECUTOR = "pushTaskExecutor";

	/**
	 * 웹 푸시 전송용 스레드 풀. 큐가 넘치면 그 알림은 버리고 로그만 남긴다(알림은 부가 기능이라 요청을 막지 않음).
	 */
	@Bean(name = PUSH_EXECUTOR)
	ThreadPoolTaskExecutor pushTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("push-");
		executor.setRejectedExecutionHandler((task, pool) -> log.warn("웹 푸시 전송 큐가 가득 차 알림을 버립니다."));
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}
}
