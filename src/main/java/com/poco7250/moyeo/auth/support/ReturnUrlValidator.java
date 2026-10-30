package com.poco7250.moyeo.auth.support;

import com.poco7250.moyeo.auth.config.AuthRedirectProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 로그인 후 돌아갈 returnUrl을 검증한다.
 * 외부 사이트로 보내는 오픈 리다이렉트를 막기 위해 같은 오리진의 상대 경로만 허용한다.
 */
@Component
@RequiredArgsConstructor
public class ReturnUrlValidator {

	private static final int MAX_LENGTH = 2048;

	private final AuthRedirectProperties properties;

	/**
	 * 안전한 상대 경로면 그대로, 아니면 기본 경로를 돌려준다.
	 */
	public String sanitize(String raw) {
		return isSafe(raw) ? raw : properties.defaultPath();
	}

	/**
	 * '/'로 시작하는 상대 경로인지 본다. '//'(프로토콜 상대 URL), 역슬래시, 줄바꿈은 브라우저가
	 * 외부 주소로 해석하거나 헤더를 깨뜨릴 수 있어 거부한다.
	 */
	private boolean isSafe(String raw) {
		if (!StringUtils.hasText(raw) || raw.length() > MAX_LENGTH) {
			return false;
		}
		if (!raw.startsWith("/") || raw.startsWith("//")) {
			return false;
		}
		return raw.indexOf('\\') < 0 && raw.indexOf('\r') < 0 && raw.indexOf('\n') < 0;
	}
}
