package com.poco7250.moyeo.auth.token;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access 토큰(JWT, HS256)을 발급하고 검증한다. subject에 memberId를 담는다.
 * 토큰 원문은 로그에 남기지 않는다.
 */
@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final Duration accessTokenTtl;
	private final Clock clock;
	private final JwtParser parser;

	/**
	 * 설정의 secret으로 서명 키를 만들고, 만료 판정에 주입받은 Clock을 쓰는 파서를 준비한다.
	 */
	public JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenTtl = properties.accessTokenTtl();
		this.clock = clock;
		this.parser = Jwts.parser()
				.verifyWith(key)
				.clock(() -> Date.from(clock.instant()))
				.build();
	}

	/**
	 * 회원 ID를 subject로 담은 Access 토큰을 발급한다.
	 */
	public String createAccessToken(Long memberId) {
		Instant now = clock.instant();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTokenTtl)))
				.signWith(key)
				.compact();
	}

	/**
	 * Access 토큰을 검증하고 회원 ID를 꺼낸다.
	 *
	 * @throws BusinessException 만료면 EXPIRED_TOKEN, 서명 불일치·형식 오류·빈 값이면 INVALID_TOKEN
	 */
	public Long parseMemberId(String token) {
		try {
			String subject = parser.parseSignedClaims(token).getPayload().getSubject();
			return Long.parseLong(subject);
		} catch (ExpiredJwtException e) {
			throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
		} catch (JwtException | IllegalArgumentException e) {
			// NumberFormatException(숫자가 아닌·없는 subject)도 IllegalArgumentException에 포함된다
			throw new BusinessException(ErrorCode.INVALID_TOKEN);
		}
	}
}
