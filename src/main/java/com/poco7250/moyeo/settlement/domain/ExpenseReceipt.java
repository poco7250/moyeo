package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지출 1건에 붙는 영수증 사진 1장(PRD S-1 확장). 파일 내용은 저장소에 두고 여기엔 파일 이름·형식·크기만 둔다.
 * Expense에 컬럼으로 넣지 않은 이유: Expense는 @Version이 없고 전체 컬럼을 UPDATE하므로
 * 지출 수정과 사진 교체가 겹치면 서로 덮어쓰게 된다. 따로 두면 금액 쓰기와 사진 쓰기가 같은 행을 건드리지 않는다.
 */
@Getter
@Entity
@Table(
		name = "expense_receipt",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_expense_receipt_expense_id", columnNames = "expense_id"),
				@UniqueConstraint(name = "uk_expense_receipt_storage_key", columnNames = "storage_key")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseReceipt extends BaseTimeEntity {

	/** 올릴 수 있는 최대 크기(10MB). 컨테이너 multipart 제한과 같은 값이다 */
	public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_id", nullable = false)
	private Expense expense;

	@Column(name = "storage_key", nullable = false, length = 64)
	private String storageKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ReceiptImageType imageType;

	@Column(nullable = false)
	private long size;

	@Column(nullable = false)
	private Long uploadedBy;

	@Column(nullable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime uploadedAt;

	@Version
	private Long version;

	private ExpenseReceipt(Expense expense, String storageKey, ReceiptImageType imageType, long size,
			Long uploadedBy, LocalDateTime uploadedAt) {
		this.expense = expense;
		apply(storageKey, imageType, size, uploadedBy, uploadedAt);
	}

	/**
	 * 지출에 영수증 사진을 처음 붙인다.
	 */
	public static ExpenseReceipt create(Expense expense, String storageKey, ReceiptImageType imageType, long size,
			Long uploadedBy, LocalDateTime uploadedAt) {
		return new ExpenseReceipt(expense, storageKey, imageType, size, uploadedBy, uploadedAt);
	}

	/**
	 * 사진을 새 파일로 교체한다.
	 *
	 * @return 교체되어 더 이상 쓰지 않는 이전 파일 이름(커밋 뒤 저장소에서 지운다)
	 */
	public String replace(String storageKey, ReceiptImageType imageType, long size, Long uploadedBy,
			LocalDateTime uploadedAt) {
		String previousKey = this.storageKey;
		apply(storageKey, imageType, size, uploadedBy, uploadedAt);
		return previousKey;
	}

	private void apply(String storageKey, ReceiptImageType imageType, long size, Long uploadedBy,
			LocalDateTime uploadedAt) {
		this.storageKey = storageKey;
		this.imageType = imageType;
		this.size = size;
		this.uploadedBy = uploadedBy;
		// DATETIME(6)에 저장되는 값과 메모리 값을 같게 맞춘다
		this.uploadedAt = uploadedAt.truncatedTo(ChronoUnit.MICROS);
	}
}
