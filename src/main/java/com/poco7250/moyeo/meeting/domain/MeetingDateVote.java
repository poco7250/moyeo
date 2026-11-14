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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜 후보 하나에 대한 멤버 한 명의 가능/불가 응답(PRD M-5).
 * 멤버가 응답하면 그 시점의 모든 후보에 행이 생기므로, 행이 없으면 그 후보에는 아직 응답하지 않은 것이다
 * (응답 뒤에 추가된 후보가 그렇다).
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_meeting_date_vote_candidate_id_member_id", columnNames = {"candidate_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingDateVote extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "candidate_id", nullable = false)
	private MeetingDateCandidate candidate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private boolean available;

	private MeetingDateVote(MeetingDateCandidate candidate, Member member, boolean available) {
		this.candidate = candidate;
		this.member = member;
		this.available = available;
	}

	/**
	 * 후보에 대한 응답을 만든다.
	 */
	public static MeetingDateVote answer(MeetingDateCandidate candidate, Member member, boolean available) {
		return new MeetingDateVote(candidate, member, available);
	}

	/**
	 * 응답을 바꾼다. 같은 값이면 아무것도 바꾸지 않는다.
	 *
	 * @return 실제로 바뀌었으면 true
	 */
	public boolean change(boolean available) {
		if (this.available == available) {
			return false;
		}
		this.available = available;
		return true;
	}
}
