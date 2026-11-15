package com.poco7250.moyeo.global.crypto;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 송금 계좌번호 암호화 설정(payment.crypto.*).
 *
 * @param encryptionKey Base64로 인코딩한 32바이트 AES 키. 로컬은 config/application-local.properties,
 *                      운영은 환경 변수 PAYMENT_ENCRYPTION_KEY로 주입한다. 키를 잃으면 저장된 계좌번호를 복구할 수 없다
 */
@ConfigurationProperties(prefix = "payment.crypto")
public record PaymentCryptoProperties(String encryptionKey) {

	/** AES-256 키 길이(바이트) */
	public static final int KEY_BYTES = 32;

	/**
	 * 키가 없거나 Base64가 아니거나 32바이트가 아니면 기동을 막는다.
	 */
	public PaymentCryptoProperties {
		if (decode(encryptionKey).length != KEY_BYTES) {
			throw new IllegalArgumentException(
					"payment.crypto.encryption-key는 Base64로 인코딩한 " + KEY_BYTES + "바이트 키여야 합니다.");
		}
	}

	/**
	 * 디코딩한 키 바이트를 돌려준다.
	 */
	public byte[] keyBytes() {
		return decode(encryptionKey);
	}

	/**
	 * 키를 가린 문자열을 돌려준다(설정 덤프·로그로 키가 새지 않도록).
	 */
	@Override
	public String toString() {
		return "PaymentCryptoProperties[encryptionKey=****]";
	}

	private static byte[] decode(String key) {
		if (key == null || key.isBlank()) {
			return new byte[0];
		}
		try {
			return Base64.getDecoder().decode(key.strip());
		} catch (IllegalArgumentException e) {
			return new byte[0];
		}
	}
}
