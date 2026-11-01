package com.poco7250.moyeo.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정(PRD 6장 실시간).
 * <ul>
 *   <li>엔드포인트 /ws (네이티브 WebSocket, SockJS 없음). 핸드셰이크는 시큐리티 체인이 access_token 쿠키로 인증한다</li>
 *   <li>서버 발행 토픽 /topic (simple broker), 클라이언트 → 서버 메시지 prefix /app</li>
 *   <li>구독 인가는 {@link SubscriptionAuthorizationInterceptor}, 에러 프레임은 {@link StompErrorHandler}</li>
 * </ul>
 * 끊겼다 다시 붙은 클라이언트는 REST로 현재 상태를 받아 복구한다(메시지 재전송 없음).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** STOMP 핸드셰이크 엔드포인트 */
	public static final String ENDPOINT = "/ws";

	private final RealtimeProperties realtimeProperties;
	private final SubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor;
	private final StompErrorHandler stompErrorHandler;

	/**
	 * 엔드포인트와 허용 오리진을 등록한다. 허용 오리진이 비어 있으면 같은 오리진만 받는다.
	 */
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(ENDPOINT)
				.setAllowedOriginPatterns(realtimeProperties.allowedOrigins().toArray(String[]::new));
		registry.setErrorHandler(stompErrorHandler);
	}

	/**
	 * 메모리 브로커와 prefix를 설정한다.
	 */
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	/**
	 * 클라이언트 inbound 채널에 구독 인가 인터셉터를 건다.
	 */
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(subscriptionAuthorizationInterceptor);
	}
}
