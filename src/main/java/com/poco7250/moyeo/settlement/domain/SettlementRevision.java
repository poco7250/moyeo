package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 정산 확정 스냅샷. 확정·재확정마다 1건 만들고 이후 수정하지 않는다.
 */
@Getter
@Entity
@Immutable
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_settlement_revision_settlement_id_revision_no",
				columnNames = {"settlement_id", "revision_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "settlement_id", nullable = false)
	private Settlement settlement;

	@Column(nullable = false)
	private int revisionNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "confirmed_by", nullable = false)
	private Member confirmedBy;

	@Column(nullable = false)
	private LocalDateTime confirmedAt;

	// 계산 경고가 있었고 호스트가 확인한 뒤 확정했으면 true
	@Column(nullable = false)
	private boolean warningsAcknowledged;

	private SettlementRevision(Settlement settlement, int revisionNo, Member confirmedBy, LocalDateTime confirmedAt,
			boolean warningsAcknowledged) {
		this.settlement = settlement;
		this.revisionNo = revisionNo;
		this.confirmedBy = confirmedBy;
		this.confirmedAt = confirmedAt;
		this.warningsAcknowledged = warningsAcknowledged;
	}

	/**
	 * 확정 시점의 리비전을 만든다.
	 */
	public static SettlementRevision create(Settlement settlement, int revisionNo, Member confirmedBy,
			LocalDateTime confirmedAt, boolean warningsAcknowledged) {
		return new SettlementRevision(settlement, revisionNo, confirmedBy, confirmedAt, warningsAcknowledged);
	}
}
