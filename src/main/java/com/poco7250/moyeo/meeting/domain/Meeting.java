package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.group.domain.Group;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 안의 모임. 차수(Round)·정산(Settlement)이 모임에 붙는다.
 * 호스트는 모임을 만든 사람으로 고정되며(PRD D4) 위임하지 않는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private Group group;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(length = 1000)
	private String memo;

	// 참석 응답 마감 시각. 없으면 마감 없이 언제든 응답을 바꿀 수 있다(Task 013)
	private LocalDateTime responseDeadline;

	// 호스트 = 모임 생성자(PRD D4). 권한 검사는 isHost로만 한다
	@Column(nullable = false, updatable = false)
	private Long hostMemberId;

	private Meeting(Group group, String title, LocalDateTime startAt, String memo,
			LocalDateTime responseDeadline, Long hostMemberId) {
		this.group = group;
		this.title = title;
		this.startAt = startAt;
		this.memo = memo;
		this.responseDeadline = responseDeadline;
		this.hostMemberId = hostMemberId;
	}

	/**
	 * 새 모임을 만든다. 만든 회원이 호스트가 된다.
	 */
	public static Meeting create(Group group, String title, LocalDateTime startAt, String memo,
			LocalDateTime responseDeadline, Long hostMemberId) {
		return new Meeting(group, title, startAt, memo, responseDeadline, hostMemberId);
	}

	/**
	 * 주어진 회원이 이 모임의 호스트(생성자)인지 확인한다.
	 */
	public boolean isHost(Long memberId) {
		return memberId != null && Objects.equals(hostMemberId, memberId);
	}

	/**
	 * 참석 응답이 마감됐는지 확인한다. 마감 시각 정각까지는 열려 있고, 마감이 없으면 항상 열려 있다.
	 */
	public boolean isResponseClosed(LocalDateTime now) {
		return responseDeadline != null && now.isAfter(responseDeadline);
	}
}
