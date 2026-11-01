package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차수별 참석 응답. 다시 응답하면 기존 행을 고친다(차수당 1인 1건, (round, member) 유니크).
 */
@Getter
@Entity
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_attendance_round_id_member_id",
				columnNames = {"round_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AttendanceStatus status;

	// 현장 체크인 여부(응답과 별개)
	@Column(nullable = false)
	private boolean checkedIn;

	// 마지막으로 응답한 시각(응답률 지표 기준)
	@Column(nullable = false)
	private LocalDateTime respondedAt;

	private Attendance(Round round, Member member, AttendanceStatus status, LocalDateTime respondedAt) {
		this.round = round;
		this.member = member;
		this.status = status;
		this.respondedAt = respondedAt;
	}

	/**
	 * 차수에 대한 첫 참석 응답을 만든다.
	 */
	public static Attendance respond(Round round, Member member, AttendanceStatus status, LocalDateTime now) {
		return new Attendance(round, member, status, now);
	}

	/**
	 * 응답을 바꾸고 응답 시각을 갱신한다.
	 */
	public void changeStatus(AttendanceStatus status, LocalDateTime now) {
		this.status = status;
		this.respondedAt = now;
	}
}
