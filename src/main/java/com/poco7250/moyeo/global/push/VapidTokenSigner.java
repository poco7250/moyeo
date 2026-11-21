package com.poco7250.moyeo.global.push;

import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;

/**
 * VAPID(RFC 8292) Authorization 헤더를 만든다. ES256 JWT(aud = 푸시 서비스 오리진, exp, sub)와 공개키를 함께 보낸다.
 */
public final class VapidTokenSigner {

	/** 토큰 유효 시간. 규격상 24시간을 넘으면 안 된다 */
	static final Duration TOKEN_TTL = Duration.ofHours(12);

	private final ECPrivateKey privateKey;
	private final String publicKeyBase64Url;
	private final String subject;
	private final Clock clock;

	/**
	 * base64url raw 키로 서명기를 만든다.
	 *
	 * @throws IllegalArgumentException 키 형식이 틀리면
	 */
	public VapidTokenSigner(String publicKeyBase64Url, String privateKeyBase64Url, String subject, Clock clock) {
		// 공개키도 읽어 봐서 형식이 틀리면 기동 시점에 바로 실패하게 한다
		EcKeys.publicKey(EcKeys.decodeBase64Url(publicKeyBase64Url));
		this.privateKey = EcKeys.privateKey(EcKeys.decodeBase64Url(privateKeyBase64Url));
		this.publicKeyBase64Url = publicKeyBase64Url;
		this.subject = subject;
		this.clock = clock;
	}

	/**
	 * 구독 endpoint로 보낼 Authorization 헤더 값("vapid t=…, k=…")을 만든다.
	 */
	public String authorization(String endpoint) {
		String token = Jwts.builder()
				.audience().add(origin(endpoint)).and()
				.expiration(Date.from(clock.instant().plus(TOKEN_TTL)))
				.subject(subject)
				.signWith(privateKey, Jwts.SIG.ES256)
				.compact();
		return "vapid t=" + token + ", k=" + publicKeyBase64Url;
	}

	/**
	 * endpoint의 오리진(스킴://호스트[:포트])을 돌려준다.
	 */
	static String origin(String endpoint) {
		URI uri = URI.create(endpoint);
		String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
		return uri.getScheme() + "://" + uri.getHost() + port;
	}
}
