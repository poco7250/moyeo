package com.poco7250.moyeo.global.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.support.TestClocks;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * VAPID(RFC 8292) Authorization 헤더 단위 테스트.
 */
class VapidTokenSignerTest {

	// 서명 검증은 실제 시각으로 만료를 보므로 먼 미래로 고정한다
	private static final Clock CLOCK = TestClocks.fixed(LocalDateTime.of(2099, 1, 1, 12, 0));

	@Test
	@DisplayName("헤더는 vapid t=JWT, k=공개키이고 JWT는 공개키로 검증되며 aud는 endpoint 오리진, exp는 12시간 뒤다")
	void authorization() throws Exception {
		KeyPair keys = newKeyPair();
		String publicKey = EcKeys.encodeBase64Url(EcKeys.encode((ECPublicKey) keys.getPublic()));
		VapidTokenSigner signer = new VapidTokenSigner(publicKey, rawPrivate(keys), "mailto:admin@moyeo.invalid",
				CLOCK);

		String header = signer.authorization("https://fcm.googleapis.com/fcm/send/abc?x=1");

		assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
		String token = header.substring("vapid t=".length(), header.indexOf(", k="));
		Claims claims = Jwts.parser().verifyWith(keys.getPublic()).clock(() -> java.util.Date.from(CLOCK.instant()))
				.build().parseSignedClaims(token).getPayload();
		assertThat(claims.getAudience()).containsExactly("https://fcm.googleapis.com");
		assertThat(claims.getSubject()).isEqualTo("mailto:admin@moyeo.invalid");
		assertThat(claims.getExpiration().toInstant()).isEqualTo(CLOCK.instant().plus(VapidTokenSigner.TOKEN_TTL));
	}

	@Test
	@DisplayName("오리진은 포트가 있으면 포함하고 경로·쿼리는 뺀다")
	void origin() {
		assertThat(VapidTokenSigner.origin("https://web.push.apple.com:8443/QGx?y")).isEqualTo(
				"https://web.push.apple.com:8443");
	}

	@Test
	@DisplayName("키 형식이 틀리면 만들 때 바로 실패한다")
	void rejectsMalformedKeys() throws Exception {
		KeyPair keys = newKeyPair();
		String publicKey = EcKeys.encodeBase64Url(EcKeys.encode((ECPublicKey) keys.getPublic()));

		assertThatThrownBy(() -> new VapidTokenSigner("AAAA", rawPrivate(keys), "mailto:a@b.c", CLOCK))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VapidTokenSigner(publicKey, "AAAA", "mailto:a@b.c", CLOCK))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static KeyPair newKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(EcKeys.parameters());
		return generator.generateKeyPair();
	}

	/**
	 * 개인키 s 값을 32바이트 raw로 꺼낸다(BigInteger 부호 바이트·앞자리 0 보정).
	 */
	private static String rawPrivate(KeyPair keys) {
		byte[] bytes = ((ECPrivateKey) keys.getPrivate()).getS().toByteArray();
		byte[] raw = new byte[32];
		int length = Math.min(bytes.length, 32);
		System.arraycopy(bytes, bytes.length - length, raw, 32 - length, length);
		return EcKeys.encodeBase64Url(Arrays.copyOf(raw, 32));
	}
}
