package com.poco7250.moyeo.group.dto;

import com.poco7250.moyeo.group.domain.InviteLink;
import java.time.LocalDateTime;

/**
 * 초대 링크 응답. url은 단톡방에 바로 붙여 넣을 수 있는 공유용 주소다.
 */
public record InviteLinkResponse(String token, String url, LocalDateTime expiresAt) {

	/**
	 * 엔티티와 공유 URL로 응답 DTO를 만든다.
	 */
	public static InviteLinkResponse of(InviteLink link, String url) {
		return new InviteLinkResponse(link.getToken(), url, link.getExpiresAt());
	}
}
