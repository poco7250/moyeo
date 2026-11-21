package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임의 마지막 리마인드 푸시 기록(M-6 웹 푸시, Task 056). 모임당 1행이고 쿨다운 판단에만 쓴다.
 * 카톡 문구 리마인드(조회)는 여전히 아무것도 저장하지 않는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceReminderPush extends BaseTimeEntity {

	/** 같은 모임에 리마인드 푸시를 다시 보낼 수 있을 때까지의 시간 */
	public static final Duration COOLDOWN = Duration.ofHours(1);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false, unique = true)
	private Meeting meeting;

	@Column(nullable = false)
	private LocalDateTime lastSentAt;

	// 마지막으로 보낸 회원
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "last_sent_by_id", nullable = false)
	private Member lastSentBy;

	private AttendanceReminderPush(Meeting meeting, Member sentBy, LocalDateTime now) {
		this.meeting = meeting;
		this.lastSentBy = sentBy;
		this.lastSentAt = now;
	}

	/**
	 * 모임의 첫 리마인드 푸시를 기록한다.
	 */
	public static AttendanceReminderPush first(Meeting meeting, Member sentBy, LocalDateTime now) {
		return new AttendanceReminderPush(meeting, sentBy, now);
	}

	/**
	 * 다시 보낼 수 있는 시각.
	 */
	public LocalDateTime nextAvailableAt() {
		return lastSentAt.plus(COOLDOWN);
	}

	/**
	 * 아직 쿨다운 중인지 본다. 정확히 1시간이 지난 시각부터 다시 보낼 수 있다.
	 */
	public boolean isCoolingDown(LocalDateTime now) {
		return now.isBefore(nextAvailableAt());
	}

	/**
	 * 다시 보낸 것으로 기록한다.
	 */
	public void resend(Member sentBy, LocalDateTime now) {
		this.lastSentBy = sentBy;
		this.lastSentAt = now;
	}
}
