package com.poco7250.moyeo.global.websocket;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 실시간(WebSocket) 설정(realtime.websocket.*).
 *
 * @param allowedOrigins 핸드셰이크를 허용할 오리진 패턴. 비어 있으면 같은 오리진만 허용한다.
 *                       SPA 배포 방식(PRD 오픈 이슈 1) 결정 뒤 채운다. 예: https://moyeo.example.com
 */
@ConfigurationProperties(prefix = "realtime.websocket")
public record RealtimeProperties(@DefaultValue List<String> allowedOrigins) {
}
