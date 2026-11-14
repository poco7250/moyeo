package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
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

/**
 * 날짜 미정 모임의 날짜 후보(날짜 + 시작 시각, PRD M-5). 호스트만 만들고, 확정 뒤에도 이력으로 남는다.
 * 같은 모임에 같은 시각 후보는 하나만 둔다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_meeting_date_candidate_meeting_id_start_at", columnNames = {"meeting_id", "start_at"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingDateCandidate extends BaseTimeEntity {

	/** 모임 하나에 둘 수 있는 최대 후보 수 */
	public static final int MAX_PER_MEETING = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	private MeetingDateCandidate(Meeting meeting, LocalDateTime startAt) {
		this.meeting = meeting;
		this.startAt = startAt;
	}

	/**
	 * 모임에 날짜 후보를 만든다.
	 */
	public static MeetingDateCandidate propose(Meeting meeting, LocalDateTime startAt) {
		return new MeetingDateCandidate(meeting, startAt);
	}
}
