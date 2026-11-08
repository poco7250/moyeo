package com.poco7250.moyeo.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 단톡방에 공유하는 링크 설정(share.*). 이 주소는 서버가 OG 메타 HTML을 내려주는 공개 경로다.
 *
 * @param baseUrl 서버 공개 주소. 비어 있으면 상대 경로를 준다. og:url이 절대 주소여야 하므로 배포 전에 채운다
 */
@ConfigurationProperties(prefix = "share")
public record ShareLinkProperties(@DefaultValue("") String baseUrl) {

	/**
	 * 모임 공유 토큰으로 공유 링크를 만든다.
	 */
	public String meetingUrl(String shareToken) {
		return baseUrl + "/share/meetings/" + shareToken;
	}

	/**
	 * 초대 토큰으로 공유 링크를 만든다.
	 */
	public String inviteUrl(String token) {
		return baseUrl + "/share/invites/" + token;
	}
}
