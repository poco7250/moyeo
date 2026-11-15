package com.poco7250.moyeo.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCryptoPropertiesTest {

	@Test
	@DisplayName("키가 없거나 Base64가 아니거나 32바이트가 아니면 기동을 막는다")
	void rejectsInvalidKey() {
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

		for (String key : new String[] {null, "", "   ", "not base64!!", shortKey}) {
			assertThatThrownBy(() -> new PaymentCryptoProperties(key))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("payment.crypto.encryption-key");
		}
	}

	@Test
	@DisplayName("32바이트 키는 받고, 문자열로 찍어도 키가 보이지 않는다")
	void acceptsAndMasks() {
		String key = Base64.getEncoder().encodeToString(new byte[32]);

		PaymentCryptoProperties properties = new PaymentCryptoProperties(key);

		assertThat(properties.keyBytes()).hasSize(32);
		assertThat(properties.toString()).doesNotContain(key).contains("****");
	}
}
