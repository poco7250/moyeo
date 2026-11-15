package com.poco7250.moyeo.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 문자열 암호화. 결과는 Base64(IV 12바이트 ‖ 암호문 + 인증 태그 16바이트)다.
 * IV를 매번 새로 뽑아 같은 평문도 암호문이 달라지고, 태그로 위변조를 감지한다.
 */
@Component
public class AesGcmCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	/**
	 * 설정의 키로 암호기를 만든다.
	 */
	public AesGcmCipher(PaymentCryptoProperties properties) {
		this.key = new SecretKeySpec(properties.keyBytes(), "AES");
	}

	/**
	 * 평문을 암호화한다.
	 */
	public String encrypt(String plain) {
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(
					ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("암호화에 실패했습니다.", e);
		}
	}

	/**
	 * 암호문을 복호화한다.
	 *
	 * @throws IllegalStateException 형식이 틀리거나, 위변조됐거나, 다른 키로 암호화된 값이면
	 */
	public String decrypt(String encoded) {
		try {
			byte[] bytes = Base64.getDecoder().decode(encoded);
			if (bytes.length <= IV_BYTES) {
				throw new IllegalStateException("암호문 형식이 올바르지 않습니다.");
			}
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
			return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("복호화에 실패했습니다.", e);
		}
	}
}
