package com.poco7250.moyeo.settlement.service;

/**
 * 트랜잭션 결과에 따라 지울 영수증 파일. 커밋되면 deleteOnCommit을, 롤백되면 deleteOnRollback을 지운다.
 * DB와 파일을 한 트랜잭션으로 묶을 수 없어서, 파일은 먼저 쓰고 결과가 정해진 뒤 남은 쪽을 정리한다.
 *
 * @param deleteOnCommit 커밋 뒤 지울 파일(교체·삭제된 이전 파일). 없으면 null
 * @param deleteOnRollback 롤백 뒤 지울 파일(이번에 새로 쓴 파일). 없으면 null
 */
public record ReceiptFileCleanupEvent(String deleteOnCommit, String deleteOnRollback) {

	/**
	 * 이번 트랜잭션이 롤백되면 지울 새 파일.
	 */
	public static ReceiptFileCleanupEvent onRollback(String key) {
		return new ReceiptFileCleanupEvent(null, key);
	}

	/**
	 * 이번 트랜잭션이 커밋되면 지울 이전 파일.
	 */
	public static ReceiptFileCleanupEvent onCommit(String key) {
		return new ReceiptFileCleanupEvent(key, null);
	}
}
