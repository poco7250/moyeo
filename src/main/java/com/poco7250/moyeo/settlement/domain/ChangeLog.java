package com.poco7250.moyeo.settlement.domain;

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
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 모임 단위 금액 영향 변경 로그. append-only라 수정·삭제 경로를 두지 않는다.
 */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	// 스케줄러가 일으킨 변경은 행위자가 없어 null
	private Long actorId;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ChangeLogEntityType entityType;

	// 삭제된 엔티티의 로그도 남아야 하므로 FK가 아닌 값으로 둔다
	@Column(nullable = false)
	private Long entityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ChangeType changeType;

	@Column(columnDefinition = "TEXT")
	private String beforeJson;

	@Column(columnDefinition = "TEXT")
	private String afterJson;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SettlementStatus settlementStatusAfter;

	private ChangeLog(Meeting meeting, Long actorId, LocalDateTime changedAt, ChangeLogEntityType entityType,
			Long entityId, ChangeType changeType, String beforeJson, String afterJson,
			SettlementStatus settlementStatusAfter) {
		this.meeting = meeting;
		this.actorId = actorId;
		this.changedAt = changedAt;
		this.entityType = entityType;
		this.entityId = entityId;
		this.changeType = changeType;
		this.beforeJson = beforeJson;
		this.afterJson = afterJson;
		this.settlementStatusAfter = settlementStatusAfter;
	}

	/**
	 * 변경 로그 한 건을 만든다. 전/후 JSON은 변경 종류에 따라 없으면 null이다.
	 */
	public static ChangeLog record(Meeting meeting, Long actorId, LocalDateTime changedAt,
			ChangeLogEntityType entityType, Long entityId, ChangeType changeType, String beforeJson,
			String afterJson, SettlementStatus settlementStatusAfter) {
		return new ChangeLog(meeting, actorId, changedAt, entityType, entityId, changeType, beforeJson, afterJson,
				settlementStatusAfter);
	}
}
