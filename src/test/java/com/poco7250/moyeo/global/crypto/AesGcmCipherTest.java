package com.poco7250.moyeo.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
	private static final String OTHER_KEY = Base64.getEncoder().encodeToString("x".repeat(32).getBytes());

	private final AesGcmCipher cipher = new AesGcmCipher(new PaymentCryptoProperties(KEY));

	@Test
	@DisplayName("암호화한 값을 복호화하면 원문이고, 암호문에 원문이 드러나지 않는다")
	void roundTrip() {
		String encrypted = cipher.encrypt("110123456789");

		assertThat(encrypted).doesNotContain("110123456789");
		assertThat(cipher.decrypt(encrypted)).isEqualTo("110123456789");
	}

	@Test
	@DisplayName("같은 원문도 매번 다른 암호문이 나온다(IV를 새로 뽑음)")
	void randomIv() {
		assertThat(cipher.encrypt("110123456789")).isNotEqualTo(cipher.encrypt("110123456789"));
	}

	@Test
	@DisplayName("위변조·다른 키·형식 오류는 복호화에 실패한다")
	void rejectsTamperedOrForeign() {
		byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("110123456789"));
		bytes[bytes.length - 1] ^= 1;
		String tampered = Base64.getEncoder().encodeToString(bytes);
		String foreign = new AesGcmCipher(new PaymentCryptoProperties(OTHER_KEY)).encrypt("110123456789");

		for (String value : new String[] {tampered, foreign, "not-base64!", Base64.getEncoder().encodeToString(new byte[5])}) {
			assertThatThrownBy(() -> cipher.decrypt(value)).isInstanceOf(IllegalStateException.class);
		}
	}
}
