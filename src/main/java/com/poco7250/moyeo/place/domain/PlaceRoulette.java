package com.poco7250.moyeo.place.domain;

import com.poco7250.moyeo.game.service.GameDrawer;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 장소 투표 동점을 룰렛으로 정한 기록(PRD P-6). 돌릴 때마다 1건 만들고 이후 수정하지 않는다.
 * 추첨은 게임 룰렛(R-3)과 같은 GameDrawer.order라 프론트가 seed로 같은 연출을 재현할 수 있다.
 */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceRoulette {

	private static final int MIN_CANDIDATES = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@Column(nullable = false)
	private long seed;

	// 룰렛 대상 후보 id(추첨 순서대로, 첫 번째가 당첨)
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "place_roulette_candidate", joinColumns = @JoinColumn(name = "place_roulette_id"))
	@OrderColumn(name = "draw_order")
	@Column(name = "candidate_id", nullable = false)
	private List<Long> candidateIds = new ArrayList<>();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "winner_id", nullable = false)
	private PlaceCandidate winner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "spun_by_id", nullable = false)
	private Member spunBy;

	@Column(nullable = false)
	private LocalDateTime spunAt;

	private PlaceRoulette(Round round, long seed, List<Long> candidateIds, PlaceCandidate winner, Member spunBy,
			LocalDateTime spunAt) {
		this.round = round;
		this.seed = seed;
		this.candidateIds = new ArrayList<>(candidateIds);
		this.winner = winner;
		this.spunBy = spunBy;
		this.spunAt = spunAt;
	}

	/**
	 * 동점 후보 중 하나를 seed로 뽑는다. 후보 id로 정렬한 뒤 섞으므로 입력 순서와 상관없이 같은 결과가 나온다.
	 *
	 * @throws BusinessException 대상 후보가 2개 미만이면 PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES
	 */
	public static PlaceRoulette spin(Round round, List<PlaceCandidate> tiedCandidates, long seed, Member spunBy,
			LocalDateTime spunAt) {
		if (tiedCandidates.size() < MIN_CANDIDATES) {
			throw new BusinessException(ErrorCode.PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES);
		}
		List<Long> ordered = GameDrawer.order(tiedCandidates.stream().map(PlaceCandidate::getId).toList(), seed);
		PlaceCandidate winner = tiedCandidates.stream()
				.filter(candidate -> candidate.getId().equals(ordered.get(0)))
				.findFirst()
				.orElseThrow();
		return new PlaceRoulette(round, seed, ordered, winner, spunBy, spunAt);
	}
}
