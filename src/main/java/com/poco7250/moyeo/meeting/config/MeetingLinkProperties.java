package com.poco7250.moyeo.meeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 모임 상세 링크 설정(meeting.link.*).
 *
 * @param baseUrl 모임 상세 URL 앞부분(SPA 주소). 비어 있으면 상대 경로(/meetings/{id})를 준다. PRD 오픈 이슈 1 결정 뒤 채운다
 */
@ConfigurationProperties(prefix = "meeting.link")
public record MeetingLinkProperties(@DefaultValue("") String baseUrl) {

	/**
	 * 모임 id로 SPA 모임 상세 URL을 만든다. 공유 링크를 연 사람을 이 주소로 보낸다.
	 */
	public String urlOf(Long meetingId) {
		return baseUrl + "/meetings/" + meetingId;
	}
}
