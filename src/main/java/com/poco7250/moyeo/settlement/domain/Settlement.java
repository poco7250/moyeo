package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임의 정산. 모임당 1건이며 모임을 만들 때 DRAFT로 함께 생성한다.
 * 금액 영향 쓰기·확정·재확정은 이 행의 version을 강제 증가시켜 직렬화한다(Task 021).
 */
@Getter
@Entity
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_settlement_meeting_id",
				columnNames = "meeting_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

	public static final int FIRST_REVISION_NO = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SettlementStatus status;

	// 마지막으로 확정된 리비전 번호(1부터). 확정 전에는 null
	private Integer currentRevisionNo;

	private LocalDateTime firstConfirmedAt;

	@Version
	private Long version;

	private Settlement(Meeting meeting) {
		this.meeting = meeting;
		this.status = SettlementStatus.DRAFT;
	}

	/**
	 * 모임의 정산을 미확정(DRAFT) 상태로 만든다.
	 */
	public static Settlement createDraft(Meeting meeting) {
		return new Settlement(meeting);
	}

	/**
	 * 확정된 정산을 재확정 필요 상태로 되돌린다. DRAFT·REOPENED는 그대로 둔다.
	 *
	 * @return 이번 호출로 CONFIRMED에서 REOPENED로 바뀌었으면 true
	 */
	public boolean reopen() {
		if (status != SettlementStatus.CONFIRMED) {
			return false;
		}
		status = SettlementStatus.REOPENED;
		return true;
	}

	/**
	 * 최초 확정이 가능한 상태인지 검사한다. REOPENED 재확정은 Task 039에서 연다.
	 *
	 * @throws BusinessException DRAFT가 아니면 SETTLEMENT_ALREADY_CONFIRMED
	 */
	public void requireConfirmable() {
		if (status != SettlementStatus.DRAFT) {
			throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_CONFIRMED);
		}
	}

	/**
	 * DRAFT 정산을 리비전 1로 확정한다.
	 *
	 * @throws BusinessException DRAFT가 아니면 SETTLEMENT_ALREADY_CONFIRMED
	 */
	public void confirmFirst(LocalDateTime now) {
		requireConfirmable();
		status = SettlementStatus.CONFIRMED;
		currentRevisionNo = FIRST_REVISION_NO;
		firstConfirmedAt = now;
	}
}
