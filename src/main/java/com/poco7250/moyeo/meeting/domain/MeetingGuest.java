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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임 게스트(PRD G-6). 호스트가 이름만으로 모임에 추가하고, 그 모임 게임 참가자로 넣을 수 있다.
 * 게스트 회원(guest = true)은 모임 하나에만 속해서, 같은 사람이 다음 모임에 와도 새 게스트로 만든다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_meeting_guest_member_id", columnNames = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingGuest extends BaseTimeEntity {

	/** 모임 하나에 둘 수 있는 최대 게스트 수 */
	public static final int MAX_PER_MEETING = 20;

	/** 게스트 이름 최대 길이 */
	public static final int NAME_MAX_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	// 게스트 회원. 이름은 member.nickname에 있다
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	// 게스트를 추가한 회원 id
	@Column(nullable = false)
	private Long createdBy;

	private MeetingGuest(Meeting meeting, Member member, Long createdBy) {
		this.meeting = meeting;
		this.member = member;
		this.createdBy = createdBy;
	}

	/**
	 * 게스트 회원을 모임에 묶는다.
	 *
	 * @throws IllegalArgumentException 게스트가 아닌 회원이면(호출 쪽 버그)
	 */
	public static MeetingGuest add(Meeting meeting, Member guest, Long createdBy) {
		if (!guest.isGuest()) {
			throw new IllegalArgumentException("게스트 회원만 모임 게스트로 둘 수 있습니다.");
		}
		return new MeetingGuest(meeting, guest, createdBy);
	}

	/**
	 * 게스트 이름.
	 */
	public String name() {
		return member.getNickname();
	}
}
