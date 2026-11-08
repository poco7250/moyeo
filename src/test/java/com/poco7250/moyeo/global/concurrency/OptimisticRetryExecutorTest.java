package com.poco7250.moyeo.global.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OptimisticRetryExecutorTest {

	private final OptimisticRetryExecutor executor = new OptimisticRetryExecutor();
	private final AtomicInteger attempts = new AtomicInteger();

	@AfterEach
	void tearDown() {
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	@DisplayName("충돌 없으면 한 번만 실행한다")
	void noConflict() {
		String result = executor.execute(failing(0));

		assertThat(result).isEqualTo("완료");
		assertThat(attempts).hasValue(1);
	}

	@Test
	@DisplayName("1회 충돌 후 재시도로 성공한다")
	void retryOnce() {
		String result = executor.execute(failing(1));

		assertThat(result).isEqualTo("완료");
		assertThat(attempts).hasValue(2);
	}

	@Test
	@DisplayName("2회 충돌해도 마지막 재시도가 성공하면 성공한다")
	void retryTwice() {
		String result = executor.execute(failing(2));

		assertThat(result).isEqualTo("완료");
		assertThat(attempts).hasValue(3);
	}

	@Test
	@DisplayName("3회 연속 충돌하면 CONCURRENT_MODIFICATION이다")
	void exhausted() {
		assertThatThrownBy(() -> executor.execute(failing(3)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
		assertThat(attempts).hasValue(3);
	}

	@Test
	@DisplayName("유니크 위반은 재시도 없이 전파한다")
	void dataIntegrityNotRetried() {
		assertThatThrownBy(() -> executor.execute(throwing(new DataIntegrityViolationException("중복"))))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(attempts).hasValue(1);
	}

	@Test
	@DisplayName("BusinessException은 재시도 없이 전파한다")
	void businessExceptionNotRetried() {
		assertThatThrownBy(() -> executor.execute(throwing(new BusinessException(ErrorCode.NOT_MEETING_HOST))))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.NOT_MEETING_HOST);
		assertThat(attempts).hasValue(1);
	}

	@Test
	@DisplayName("이미 트랜잭션 안이면 실행을 거부한다")
	void rejectsActiveTransaction() {
		TransactionSynchronizationManager.setActualTransactionActive(true);

		assertThatThrownBy(() -> executor.execute(failing(0))).isInstanceOf(IllegalStateException.class);
		assertThat(attempts).hasValue(0);
	}

	private Supplier<String> failing(int failures) {
		return () -> {
			if (attempts.incrementAndGet() <= failures) {
				throw new ObjectOptimisticLockingFailureException("Settlement", 1L);
			}
			return "완료";
		};
	}

	private Supplier<String> throwing(RuntimeException exception) {
		return () -> {
			attempts.incrementAndGet();
			throw exception;
		};
	}
}
