package com.poco7250.moyeo.global.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 웹 푸시 암호화(RFC 8291) 단위 테스트. RFC 8291 부록 A의 예시 값을 그대로 재현한다.
 */
class WebPushEncryptorTest {

	// RFC 8291 부록 A
	private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
	private static final String AS_PUBLIC =
			"BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
	private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
	private static final String UA_PUBLIC =
			"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
	private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
	private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
	private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
	private static final String EXPECTED_BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6"
			+ "e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxs"
			+ "j_Qulcy4a-fN";

	@Test
	@DisplayName("RFC 8291 부록 A의 키·salt로 암호화하면 예시 본문과 바이트까지 같다")
	void rfc8291Example() {
		KeyPair ephemeral = new KeyPair(EcKeys.publicKey(decode(AS_PUBLIC)), EcKeys.privateKey(decode(AS_PRIVATE)));

		byte[] body = WebPushEncryptor.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), decode(UA_PUBLIC),
				decode(AUTH_SECRET), ephemeral, decode(SALT));

		assertThat(EcKeys.encodeBase64Url(body)).isEqualTo(EXPECTED_BODY);
	}

	@Test
	@DisplayName("임시 키·salt를 새로 만들어도 기기 개인키로 풀면 원래 평문이 나온다")
	void roundTrip() throws Exception {
		byte[] payload = "{\"title\":\"연말 모임 참석 응답 부탁해\"}".getBytes(StandardCharsets.UTF_8);

		byte[] body = WebPushEncryptor.encrypt(payload, decode(UA_PUBLIC), decode(AUTH_SECRET));

		assertThat(decrypt(body)).isEqualTo(payload);
		assertThat(Arrays.copyOfRange(body, 16, 20)).containsExactly(0, 0, 0x10, 0);
	}

	@Test
	@DisplayName("본문이 4,096바이트를 넘는 평문과 16바이트가 아닌 auth는 거절한다")
	void rejectsOversizedPayloadAndBadAuth() {
		byte[] max = new byte[WebPushEncryptor.MAX_PAYLOAD_LENGTH];

		assertThat(WebPushEncryptor.encrypt(max, decode(UA_PUBLIC), decode(AUTH_SECRET)))
				.hasSize(WebPushEncryptor.MAX_BODY_LENGTH);
		assertThatThrownBy(() -> WebPushEncryptor.encrypt(new byte[max.length + 1], decode(UA_PUBLIC),
				decode(AUTH_SECRET))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WebPushEncryptor.encrypt(max, decode(UA_PUBLIC), new byte[15]))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WebPushEncryptor.encrypt(max, new byte[65], decode(AUTH_SECRET)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * 브라우저 쪽 복호화(RFC 8291 3.4·RFC 8188)를 그대로 따라 한다.
	 */
	private static byte[] decrypt(byte[] body) throws Exception {
		ByteBuffer buffer = ByteBuffer.wrap(body);
		byte[] salt = new byte[16];
		buffer.get(salt).getInt();
		byte[] serverKey = new byte[buffer.get()];
		buffer.get(serverKey);
		byte[] cipherText = new byte[buffer.remaining()];
		buffer.get(cipherText);

		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init((ECPrivateKey) EcKeys.privateKey(decode(UA_PRIVATE)));
		agreement.doPhase((ECPublicKey) EcKeys.publicKey(serverKey), true);
		byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), decode(UA_PUBLIC), serverKey);
		byte[] ikm = WebPushEncryptor.hkdf(decode(AUTH_SECRET), agreement.generateSecret(), keyInfo, 32);
		byte[] key = WebPushEncryptor.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
		byte[] nonce = WebPushEncryptor.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
		byte[] padded = cipher.doFinal(cipherText);
		assertThat(padded[padded.length - 1]).isEqualTo((byte) 0x02);
		return Arrays.copyOf(padded, padded.length - 1);
	}

	private static byte[] concat(byte[]... parts) {
		ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(p -> p.length).sum());
		for (byte[] part : parts) {
			buffer.put(part);
		}
		return buffer.array();
	}

	private static byte[] decode(String value) {
		return EcKeys.decodeBase64Url(value);
	}
}
