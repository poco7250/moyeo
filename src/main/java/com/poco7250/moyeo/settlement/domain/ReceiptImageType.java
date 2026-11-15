package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.util.Arrays;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 영수증 사진으로 받는 이미지 형식. 클라이언트가 보낸 Content-Type은 믿지 않고 파일 앞부분 바이트(매직 바이트)로 판별한다.
 */
@Getter
@RequiredArgsConstructor
public enum ReceiptImageType {

	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
	private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};
	// WEBP는 "RIFF" + 파일 크기 4바이트 + "WEBP"
	private static final int WEBP_MARK_OFFSET = 8;

	private final String contentType;
	private final String extension;

	/**
	 * 파일 내용으로 이미지 형식을 판별한다.
	 *
	 * @throws BusinessException 비었거나 지원하는 형식이 아니면 RECEIPT_INVALID_TYPE
	 */
	public static ReceiptImageType detect(byte[] content) {
		if (content == null) {
			throw new BusinessException(ErrorCode.RECEIPT_INVALID_TYPE);
		}
		if (startsWith(content, 0, JPEG_MAGIC)) {
			return JPEG;
		}
		if (startsWith(content, 0, PNG_MAGIC)) {
			return PNG;
		}
		if (startsWith(content, 0, RIFF_MAGIC) && startsWith(content, WEBP_MARK_OFFSET, WEBP_MAGIC)) {
			return WEBP;
		}
		throw new BusinessException(ErrorCode.RECEIPT_INVALID_TYPE);
	}

	/**
	 * 저장소에 쓸 새 파일 이름(무작위 UUID + 확장자)을 만든다. 사용자 입력이 섞이지 않아 경로 조작이 불가능하다.
	 */
	public String newStorageKey() {
		return UUID.randomUUID() + "." + extension;
	}

	private static boolean startsWith(byte[] content, int offset, byte[] magic) {
		if (content.length < offset + magic.length) {
			return false;
		}
		return Arrays.equals(content, offset, offset + magic.length, magic, 0, magic.length);
	}
}
