package com.poco7250.moyeo.place.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
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
 * 장소 후보 투표. 한 후보에 회원당 1표((candidate, member) 유니크), 후보 여러 곳에 투표할 수 있다.
 */
@Getter
@Entity
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_place_vote_candidate_id_member_id",
				columnNames = {"candidate_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceVote extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "candidate_id", nullable = false)
	private PlaceCandidate candidate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	private PlaceVote(PlaceCandidate candidate, Member member) {
		this.candidate = candidate;
		this.member = member;
	}

	/**
	 * 후보에 투표한다.
	 */
	public static PlaceVote cast(PlaceCandidate candidate, Member member) {
		return new PlaceVote(candidate, member);
	}
}
