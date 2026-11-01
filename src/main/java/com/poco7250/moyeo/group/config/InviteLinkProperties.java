package com.poco7250.moyeo.group.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 초대 링크 설정(group.invite.*).
 *
 * @param ttl     발급 시각부터 만료까지 기간. PRD 미정이라 기본 7일로 둔다
 * @param baseUrl 초대 링크 URL 앞부분(SPA 주소). 비어 있으면 상대 경로(/invites/{token})를 준다. PRD 오픈 이슈 1 결정 뒤 채운다
 */
@ConfigurationProperties(prefix = "group.invite")
public record InviteLinkProperties(
		@DefaultValue("7d") Duration ttl,
		@DefaultValue("") String baseUrl) {

	/**
	 * 토큰으로 사용자에게 공유할 초대 링크 URL을 만든다.
	 */
	public String urlOf(String token) {
		return baseUrl + "/invites/" + token;
	}
}
