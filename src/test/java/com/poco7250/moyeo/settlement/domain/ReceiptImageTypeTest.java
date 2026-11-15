package com.poco7250.moyeo.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReceiptImageTypeTest {

	static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
	static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00};
	static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8'};

	@Test
	@DisplayName("앞부분 바이트로 JPEG·PNG·WEBP를 판별한다")
	void detectsSupportedTypes() {
		assertThat(ReceiptImageType.detect(JPEG)).isEqualTo(ReceiptImageType.JPEG);
		assertThat(ReceiptImageType.detect(PNG)).isEqualTo(ReceiptImageType.PNG);
		assertThat(ReceiptImageType.detect(WEBP)).isEqualTo(ReceiptImageType.WEBP);
	}

	@Test
	@DisplayName("텍스트·빈 파일·RIFF지만 WEBP가 아닌 파일·잘린 PNG는 RECEIPT_INVALID_TYPE")
	void rejectsUnsupported() {
		byte[] wav = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E'};
		byte[] truncatedPng = {(byte) 0x89, 'P', 'N', 'G'};

		for (byte[] content : new byte[][] {"<svg/>".getBytes(StandardCharsets.UTF_8), new byte[0], wav,
				truncatedPng}) {
			assertThatThrownBy(() -> ReceiptImageType.detect(content))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.RECEIPT_INVALID_TYPE);
		}
	}

	@Test
	@DisplayName("저장 파일 이름은 UUID + 형식 확장자이고 매번 다르다")
	void newStorageKey() {
		String first = ReceiptImageType.PNG.newStorageKey();
		String second = ReceiptImageType.PNG.newStorageKey();

		assertThat(first).matches("[0-9a-f-]{36}\\.png").isNotEqualTo(second);
	}
}
