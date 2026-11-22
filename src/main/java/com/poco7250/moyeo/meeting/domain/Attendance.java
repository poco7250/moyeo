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

	// 참석으로 응답했는데 실제 참석자 확정 때 체크인하지 않은 노쇼(PRD M-8). 응답이 다시 ATTEND가 되면 풀린다
	@Column(nullable = false)
	private boolean noShow;

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
	 * 응답 없이 현장에 와서 체크인한 멤버의 행을 만든다. 상태는 미정(UNDECIDED)이라 확정 전까지 정산에 영향이 없다.
	 */
	public static Attendance checkInWithoutResponse(Round round, Member member, LocalDateTime now) {
		Attendance attendance = new Attendance(round, member, AttendanceStatus.UNDECIDED, now);
		attendance.checkedIn = true;
		return attendance;
	}

	/**
	 * 체크인 여부를 바꾼다. 응답 상태는 그대로다(실제 참석자 확정 때 반영).
	 *
	 * @return 값이 실제로 바뀌었으면 true
	 */
	public boolean changeCheckIn(boolean checkedIn) {
		if (this.checkedIn == checkedIn) {
			return false;
		}
		this.checkedIn = checkedIn;
		return true;
	}

	/**
	 * 실제 참석자 확정 직전에 부른다. 참석(ATTEND)으로 응답했는데 체크인하지 않았으면 노쇼로 남긴다.
	 * 이미 노쇼인 행은 그대로 둔다(재확정 때도 체크인 안 했으면 유지).
	 */
	public void recordNoShowIfMissed() {
		if (status == AttendanceStatus.ATTEND && !checkedIn) {
			this.noShow = true;
		}
	}

	/**
	 * 응답을 바꾸고 응답 시각을 갱신한다. 참석(ATTEND)으로 바뀌면 노쇼 기록을 푼다.
	 */
	public void changeStatus(AttendanceStatus status, LocalDateTime now) {
		this.status = status;
		this.respondedAt = now;
		if (status == AttendanceStatus.ATTEND) {
			this.noShow = false;
		}
	}
}
