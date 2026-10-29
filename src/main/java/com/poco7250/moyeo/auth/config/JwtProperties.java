package com.poco7250.moyeo.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 자체 JWT 설정(auth.jwt.*).
 * secret은 application-local.properties나 환경 변수(JWT_SECRET)로만 넣는다.
 *
 * @param secret          HS256 서명 키(UTF-8 32바이트 이상, 로그·toString에 노출 금지)
 * @param accessTokenTtl  Access 토큰 유효 시간
 * @param refreshTokenTtl Refresh 토큰 유효 시간
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
		String secret,
		@DefaultValue("30m") Duration accessTokenTtl,
		@DefaultValue("14d") Duration refreshTokenTtl) {

	/** HS256 키 최소 길이(바이트). */
	public static final int MIN_SECRET_BYTES = 32;

	/**
	 * 서명 키가 HS256 최소 길이보다 짧으면 기동을 막는다.
	 */
	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"auth.jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
		}
	}

	/**
	 * secret을 가린 문자열을 돌려준다(설정 덤프·로그로 시크릿이 새지 않도록).
	 */
	@Override
	public String toString() {
		return "JwtProperties[secret=****"
				+ ", accessTokenTtl=" + accessTokenTtl
				+ ", refreshTokenTtl=" + refreshTokenTtl + "]";
	}
}
