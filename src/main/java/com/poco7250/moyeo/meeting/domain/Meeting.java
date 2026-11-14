package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.common.RandomTokens;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 안의 모임. 차수(Round)·정산(Settlement)이 모임에 붙는다.
 * 호스트는 모임을 만든 사람으로 고정되며(PRD D4) 위임하지 않는다.
 * 날짜가 미정이면 날짜 투표(VOTING) 상태로 만들어 startAt을 비워 두고, 호스트가 후보를 확정하면 채운다(PRD M-5).
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_meeting_share_token", columnNames = "share_token"))
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

	// 날짜 투표 중(VOTING)이면 비어 있고, 확정(CONFIRMED)되면 항상 있다
	private LocalDateTime startAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MeetingDateStatus dateStatus;

	@Column(length = 1000)
	private String memo;

	// 참석 응답 마감 시각. 없으면 마감 없이 언제든 응답을 바꿀 수 있다(Task 013)
	private LocalDateTime responseDeadline;

	// 호스트 = 모임 생성자(PRD D4). 권한 검사는 isHost로만 한다
	@Column(nullable = false, updatable = false)
	private Long hostMemberId;

	// 비로그인 OG 공유 링크용 토큰. 순번 id 대입으로 다른 모임을 보지 못하게 id 대신 쓴다
	@Column(name = "share_token", nullable = false, updatable = false, length = RandomTokens.LENGTH)
	private String shareToken;

	// 날짜 확정이 동시에 들어오면 한쪽만 반영한다
	@Version
	private Long version;

	private Meeting(Group group, String title, LocalDateTime startAt, String memo,
			LocalDateTime responseDeadline, Long hostMemberId) {
		this.group = group;
		this.title = title;
		this.startAt = startAt;
		this.dateStatus = startAt == null ? MeetingDateStatus.VOTING : MeetingDateStatus.CONFIRMED;
		this.memo = memo;
		this.responseDeadline = responseDeadline;
		this.hostMemberId = hostMemberId;
		this.shareToken = RandomTokens.urlSafe();
	}

	/**
	 * 날짜가 정해진 새 모임을 만든다. 만든 회원이 호스트가 된다.
	 *
	 * @throws IllegalArgumentException 시작 시각이 없으면(날짜 미정은 createWithDateVote로 만든다)
	 */
	public static Meeting create(Group group, String title, LocalDateTime startAt, String memo,
			LocalDateTime responseDeadline, Long hostMemberId) {
		if (startAt == null) {
			throw new IllegalArgumentException("날짜가 정해진 모임은 시작 시각이 필요합니다");
		}
		return new Meeting(group, title, startAt, memo, responseDeadline, hostMemberId);
	}

	/**
	 * 날짜 미정인 새 모임을 날짜 투표 상태로 만든다. 후보는 MeetingDateCandidate로 따로 저장한다.
	 */
	public static Meeting createWithDateVote(Group group, String title, String memo, LocalDateTime responseDeadline,
			Long hostMemberId) {
		return new Meeting(group, title, null, memo, responseDeadline, hostMemberId);
	}

	/**
	 * 날짜 투표 중인지 확인한다.
	 */
	public boolean isDateVoting() {
		return dateStatus == MeetingDateStatus.VOTING;
	}

	/**
	 * 날짜 투표 중이 아니면 막는다(후보 추가·투표·확정 앞에서 호출).
	 *
	 * @throws BusinessException 날짜가 이미 확정됐으면 MEETING_DATE_NOT_VOTING
	 */
	public void requireDateVoting() {
		if (!isDateVoting()) {
			throw new BusinessException(ErrorCode.MEETING_DATE_NOT_VOTING);
		}
	}

	/**
	 * 고른 후보의 시각으로 날짜를 확정한다(VOTING → CONFIRMED).
	 *
	 * @throws BusinessException 날짜 투표 중이 아니면 MEETING_DATE_NOT_VOTING
	 * @throws IllegalArgumentException 다른 모임의 후보면(호출 쪽 버그 방어)
	 */
	public void confirmDate(MeetingDateCandidate candidate) {
		requireDateVoting();
		if (!Objects.equals(candidate.getMeeting().getId(), id)) {
			throw new IllegalArgumentException("이 모임의 날짜 후보가 아닙니다");
		}
		this.startAt = candidate.getStartAt();
		this.dateStatus = MeetingDateStatus.CONFIRMED;
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

	/**
	 * 체크인·실제 참석자 확정이 열렸는지 본다(PRD M-7). 모임 날짜(startAt) 0시부터 열리고, 날짜 투표 중이면 닫혀 있다.
	 */
	public boolean isCheckInOpen(LocalDateTime now) {
		return startAt != null && !now.toLocalDate().isBefore(startAt.toLocalDate());
	}

	/**
	 * 체크인·실제 참석자 확정이 열렸는지 검사한다.
	 *
	 * @throws BusinessException 모임 당일 전이거나 날짜 투표 중이면 CHECK_IN_NOT_OPEN
	 */
	public void requireCheckInOpen(LocalDateTime now) {
		if (!isCheckInOpen(now)) {
			throw new BusinessException(ErrorCode.CHECK_IN_NOT_OPEN);
		}
	}
}
