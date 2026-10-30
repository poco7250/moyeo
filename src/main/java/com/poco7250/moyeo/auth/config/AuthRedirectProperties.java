package com.poco7250.moyeo.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 로그인 후 리다이렉트 설정(auth.redirect.*).
 *
 * @param frontendBaseUrl SPA 주소. 비어 있으면 같은 오리진 상대 경로로 보낸다. PRD 오픈 이슈 1 결정 뒤 채운다
 * @param defaultPath     returnUrl이 없거나 안전하지 않을 때 보낼 경로
 */
@ConfigurationProperties(prefix = "auth.redirect")
public record AuthRedirectProperties(
		@DefaultValue("") String frontendBaseUrl,
		@DefaultValue("/") String defaultPath) {
}
