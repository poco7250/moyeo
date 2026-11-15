package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.storage.ReceiptStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션이 끝난 뒤 쓰지 않게 된 영수증 파일을 지운다. 이미 결과가 정해진 뒤라 실패는 로그만 남긴다(고아 파일만 남음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptFileCleaner {

	private final ReceiptStorage receiptStorage;

	/**
	 * 커밋되면 교체·삭제된 이전 파일을 지운다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void afterCommit(ReceiptFileCleanupEvent event) {
		deleteQuietly(event.deleteOnCommit());
	}

	/**
	 * 롤백되면 이번에 새로 쓴 파일을 지운다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
	public void afterRollback(ReceiptFileCleanupEvent event) {
		deleteQuietly(event.deleteOnRollback());
	}

	private void deleteQuietly(String key) {
		if (key == null) {
			return;
		}
		try {
			receiptStorage.delete(key);
		} catch (RuntimeException e) {
			log.warn("영수증 파일 정리 실패: key={}", key, e);
		}
	}
}
