package com.poco7250.moyeo.global.storage;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 영수증 사진을 로컬 디스크(receipt.storage.local-base-dir)에 저장한다. 단일 인스턴스 전제다.
 */
@Slf4j
@Component
public class LocalReceiptStorage implements ReceiptStorage {

	private final Path baseDir;

	/**
	 * 저장 위치를 절대 경로로 고정한다. 디렉터리는 첫 저장 때 만든다.
	 */
	public LocalReceiptStorage(ReceiptStorageProperties properties) {
		this.baseDir = Path.of(properties.localBaseDir()).toAbsolutePath().normalize();
	}

	/**
	 * 파일을 새로 만든다. 같은 이름이 이미 있으면 덮어쓰지 않고 실패한다(UUID라 실제로는 겹치지 않음).
	 */
	@Override
	public void store(String key, byte[] content) {
		Path path = resolve(key);
		try {
			Files.createDirectories(baseDir);
			Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		} catch (IOException e) {
			log.error("영수증 사진 저장 실패: key={}", key, e);
			throw new BusinessException(ErrorCode.RECEIPT_STORAGE_FAILED);
		}
	}

	@Override
	public Optional<Resource> load(String key) {
		Path path = resolve(key);
		return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * key를 저장 위치 안의 경로로 바꾼다. 서버가 만든 이름만 오지만 혹시 모를 경로 조작을 막는다.
	 *
	 * @throws IllegalArgumentException 저장 위치 밖을 가리키면
	 */
	private Path resolve(String key) {
		Path path = baseDir.resolve(key).normalize();
		if (!path.getParent().equals(baseDir)) {
			throw new IllegalArgumentException("저장 위치 밖의 경로입니다: " + key);
		}
		return path;
	}
}
