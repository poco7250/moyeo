package com.poco7250.moyeo.place.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차수의 장소 후보. 호스트가 바로 확정해도 후보 1개짜리로 만들어 확정 장소 조회 경로를 하나로 둔다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCandidate extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@Embedded
	private Place place;

	// 후보를 제안한 회원(바로 확정이면 호스트)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "proposed_by_id", nullable = false)
	private Member proposedBy;

	private PlaceCandidate(Round round, Place place, Member proposedBy) {
		this.round = round;
		this.place = place;
		this.proposedBy = proposedBy;
	}

	/**
	 * 차수에 장소 후보를 제안한다.
	 */
	public static PlaceCandidate propose(Round round, Place place, Member proposedBy) {
		return new PlaceCandidate(round, place, proposedBy);
	}

	/**
	 * 이 후보가 주어진 차수의 후보인지 본다. 지연 로딩 프록시라도 id만 비교하므로 초기화하지 않는다.
	 */
	public boolean belongsTo(Round target) {
		if (round == target) {
			return true;
		}
		return round.getId() != null && Objects.equals(round.getId(), target.getId());
	}
}
