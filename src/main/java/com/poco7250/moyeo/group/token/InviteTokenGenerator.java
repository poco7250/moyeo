package com.poco7250.moyeo.group.token;

import com.poco7250.moyeo.global.common.RandomTokens;
import org.springframework.stereotype.Component;

/**
 * 초대 링크 토큰을 만든다. 테스트에서 고정 토큰으로 바꿀 수 있게 빈으로 둔다.
 */
@Component
public class InviteTokenGenerator {

	/**
	 * 추측할 수 없는 URL 안전 토큰 43자를 만든다.
	 */
	public String generate() {
		return RandomTokens.urlSafe();
	}
}
