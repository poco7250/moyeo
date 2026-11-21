package com.poco7250.moyeo.global.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 메시지 암호화(RFC 8291, Content-Encoding aes128gcm — RFC 8188). JDK 암호 API만 쓴다.
 * 매번 임시 ECDH 키 쌍과 16바이트 salt를 새로 만들고, 레코드 하나(마지막 레코드, 패딩 없음)로 암호화한다.
 */
public final class WebPushEncryptor {

	/** 푸시 서비스가 받는 본문 최대 크기. 헤더 86바이트·구분자 1바이트·태그 16바이트를 빼면 평문은 3,993바이트까지 */
	public static final int MAX_BODY_LENGTH = 4096;

	private static final int SALT_LENGTH = 16;
	private static final int AUTH_SECRET_LENGTH = 16;
	private static final int RECORD_SIZE = 4096;
	private static final int TAG_BITS = 128;
	private static final int TAG_LENGTH = TAG_BITS / 8;
	private static final int HEADER_LENGTH = SALT_LENGTH + 4 + 1 + EcKeys.PUBLIC_KEY_LENGTH;
	public static final int MAX_PAYLOAD_LENGTH = MAX_BODY_LENGTH - HEADER_LENGTH - 1 - TAG_LENGTH;
	private static final byte LAST_RECORD_DELIMITER = 0x02;
	private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
	private static final SecureRandom RANDOM = new SecureRandom();

	private WebPushEncryptor() {
	}

	/**
	 * 구독 키(p256dh·auth)로 평문을 암호화해 요청 본문(헤더 + 암호문)을 돌려준다.
	 *
	 * @param userAgentPublicKey 구독의 p256dh(raw 65바이트)
	 * @param authSecret         구독의 auth(16바이트)
	 * @throws IllegalArgumentException 키 형식이 틀리거나 평문이 {@link #MAX_PAYLOAD_LENGTH}를 넘으면
	 */
	public static byte[] encrypt(byte[] payload, byte[] userAgentPublicKey, byte[] authSecret) {
		byte[] salt = new byte[SALT_LENGTH];
		RANDOM.nextBytes(salt);
		return encrypt(payload, userAgentPublicKey, authSecret, newEphemeralKeyPair(), salt);
	}

	/**
	 * 임시 키 쌍과 salt를 받아 암호화한다. 테스트에서 RFC 8291 부록 A 예시를 그대로 재현하려고 연다.
	 */
	static byte[] encrypt(byte[] payload, byte[] userAgentPublicKey, byte[] authSecret, KeyPair ephemeral,
			byte[] salt) {
		if (payload.length > MAX_PAYLOAD_LENGTH) {
			throw new IllegalArgumentException("푸시 페이로드가 너무 큽니다: " + payload.length + "바이트");
		}
		if (authSecret.length != AUTH_SECRET_LENGTH) {
			throw new IllegalArgumentException("auth 비밀값은 16바이트여야 합니다.");
		}
		ECPublicKey userAgentKey = EcKeys.publicKey(userAgentPublicKey);
		byte[] serverPublicKey = EcKeys.encode((ECPublicKey) ephemeral.getPublic());
		try {
			byte[] ecdhSecret = ecdh(ephemeral, userAgentKey);
			byte[] ikm = hkdf(authSecret, ecdhSecret, concat(KEY_INFO_PREFIX, userAgentPublicKey, serverPublicKey), 32);
			byte[] contentKey = hkdf(salt, ikm, CEK_INFO, 16);
			byte[] nonce = hkdf(salt, ikm, NONCE_INFO, 12);
			byte[] cipherText = seal(contentKey, nonce, concat(payload, new byte[] {LAST_RECORD_DELIMITER}));
			return concat(header(salt, serverPublicKey), cipherText);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("푸시 메시지를 암호화하지 못했습니다.", e);
		}
	}

	/**
	 * aes128gcm 헤더: salt(16) || 레코드 크기(4, 빅엔디언) || keyid 길이(1) || keyid(서버 임시 공개키 65).
	 */
	private static byte[] header(byte[] salt, byte[] serverPublicKey) {
		return ByteBuffer.allocate(HEADER_LENGTH)
				.put(salt)
				.putInt(RECORD_SIZE)
				.put((byte) serverPublicKey.length)
				.put(serverPublicKey)
				.array();
	}

	private static KeyPair newEphemeralKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(EcKeys.parameters(), RANDOM);
			return generator.generateKeyPair();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("임시 ECDH 키를 만들지 못했습니다.", e);
		}
	}

	private static byte[] ecdh(KeyPair ephemeral, ECPublicKey userAgentKey) throws GeneralSecurityException {
		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(ephemeral.getPrivate());
		agreement.doPhase(userAgentKey, true);
		return agreement.generateSecret();
	}

	/**
	 * HKDF-SHA256(RFC 5869). 결과가 32바이트 이하라 expand는 블록 하나(T(1))면 된다.
	 */
	static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
		byte[] prk = hmac(salt, ikm);
		byte[] okm = hmac(prk, concat(info, new byte[] {0x01}));
		return Arrays.copyOf(okm, length);
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

	private static byte[] seal(byte[] key, byte[] nonce, byte[] plainText) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
		return cipher.doFinal(plainText);
	}

	private static byte[] concat(byte[]... parts) {
		ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(p -> p.length).sum());
		for (byte[] part : parts) {
			buffer.put(part);
		}
		return buffer.array();
	}
}
