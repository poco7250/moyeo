package com.poco7250.moyeo.global.concurrency;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 낙관적 락 충돌 시 작업을 다시 실행하는 facade용 실행기.
 * 작업은 {@code @Transactional} Service를 호출해야 하며, 매 시도가 새 트랜잭션이 되도록 트랜잭션 밖에서만 부른다.
 * 충돌은 상대가 이미 커밋한 뒤 드러나므로 대기 없이 바로 다시 읽어도 새 버전이 보인다.
 */
@Slf4j
@Component
public class OptimisticRetryExecutor {

	private static final int MAX_ATTEMPTS = 3;

	/**
	 * 작업을 실행하고, 낙관적 락 충돌이면 최대 2회 다시 실행한다. 그 외 예외는 그대로 전파한다.
	 *
	 * @throws BusinessException 재시도까지 모두 충돌하면 CONCURRENT_MODIFICATION
	 * @throws IllegalStateException 이미 트랜잭션 안에서 호출한 경우
	 */
	public <T> T execute(Supplier<T> action) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("재시도는 트랜잭션 밖에서 시작해야 합니다.");
		}
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return action.get();
			} catch (ObjectOptimisticLockingFailureException e) {
				log.info("낙관적 락 충돌: attempt={}/{}", attempt, MAX_ATTEMPTS);
			}
		}
		log.warn("낙관적 락 재시도 소진: attempts={}", MAX_ATTEMPTS);
		throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
	}
}
