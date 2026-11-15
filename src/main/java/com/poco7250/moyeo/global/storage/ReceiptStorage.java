package com.poco7250.moyeo.global.storage;

import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * 영수증 사진 파일 저장소. 지금은 로컬 디스크 구현만 있고, 배포 방식(PRD 오픈 이슈 1)이 정해지면 S3 등 구현을 추가한다.
 * key는 서버가 만든 파일 이름(UUID + 확장자)만 받는다.
 */
public interface ReceiptStorage {

	/**
	 * 파일을 저장한다.
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 쓰지 못하면 RECEIPT_STORAGE_FAILED
	 */
	void store(String key, byte[] content);

	/**
	 * 파일을 읽는다. 없으면 빈 값이다.
	 */
	Optional<Resource> load(String key);

	/**
	 * 파일을 지운다. 없으면 아무것도 하지 않는다.
	 *
	 * @throws java.io.UncheckedIOException 지우지 못하면
	 */
	void delete(String key);
}
