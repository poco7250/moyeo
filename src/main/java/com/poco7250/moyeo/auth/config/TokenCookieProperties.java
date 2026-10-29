package com.poco7250.moyeo.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 쿠키 설정(auth.cookie.*).
 *
 * @param domain 쿠키 Domain 속성. 비어 있으면 붙이지 않는다(요청 호스트 전용). PRD 오픈 이슈 1 결정 뒤 채운다
 * @param secure Secure 속성. 로컬 http 개발에서만 false로 둔다
 */
@ConfigurationProperties(prefix = "auth.cookie")
public record TokenCookieProperties(
		@DefaultValue("") String domain,
		@DefaultValue("true") boolean secure) {
}
