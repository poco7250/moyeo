package com.poco7250.moyeo.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

class LocalReceiptStorageTest {

	@TempDir
	Path tempDir;

	private Path baseDir;
	private LocalReceiptStorage storage;

	@BeforeEach
	void setUp() {
		baseDir = tempDir.resolve("receipts");
		storage = new LocalReceiptStorage(new ReceiptStorageProperties(baseDir.toString()));
	}

	@Test
	@DisplayName("저장하면 디렉터리를 만들고, 읽으면 같은 내용, 지우면 사라진다")
	void storeLoadDelete() throws IOException {
		storage.store("a.jpg", new byte[] {1, 2, 3});

		Resource resource = storage.load("a.jpg").orElseThrow();
		assertThat(resource.getContentAsByteArray()).containsExactly(1, 2, 3);

		storage.delete("a.jpg");
		assertThat(Files.exists(baseDir.resolve("a.jpg"))).isFalse();
		assertThat(storage.load("a.jpg")).isEmpty();
	}

	@Test
	@DisplayName("없는 파일은 빈 값이고, 지워도 예외가 없다")
	void missingFile() {
		assertThat(storage.load("none.jpg")).isEmpty();
		storage.delete("none.jpg");
	}

	@Test
	@DisplayName("같은 이름으로 다시 저장하면 덮어쓰지 않고 RECEIPT_STORAGE_FAILED")
	void doesNotOverwrite() throws IOException {
		storage.store("a.jpg", new byte[] {1});

		assertThatThrownBy(() -> storage.store("a.jpg", new byte[] {2}))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.RECEIPT_STORAGE_FAILED);
		assertThat(Files.readAllBytes(baseDir.resolve("a.jpg"))).containsExactly(1);
	}

	@Test
	@DisplayName("저장 위치 밖이나 하위 디렉터리를 가리키는 이름은 거부한다")
	void rejectsPathTraversal() {
		for (String key : new String[] {"../escape.jpg", "sub/a.jpg", "/etc/passwd"}) {
			assertThatThrownBy(() -> storage.store(key, new byte[] {1}))
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThat(Files.exists(tempDir.resolve("escape.jpg"))).isFalse();
	}
}
