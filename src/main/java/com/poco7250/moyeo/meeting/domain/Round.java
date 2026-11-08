package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임의 차수(1차 밥, 2차 볼링 …). 장소는 모임이 아니라 차수마다 따로 정한다.
 * 투표 자동 마감과 호스트 수동 확정이 겹치면 version 낙관적 락으로 먼저 온 쪽만 반영한다.
 * 장소 상태 전이: 바로 확정(UNDECIDED→CONFIRMED), 투표 열기(UNDECIDED·CONFIRMED·호스트 선택 대기→VOTING), 투표 확정(VOTING→CONFIRMED).
 * 호스트 선택 대기는 별도 상태가 아니라 마감 처리가 끝난 VOTING이다(placeVoteClosedAt이 있음).
 */
@Getter
@Entity
// SQL ROUND 함수와 헷갈리지 않도록 테이블명을 따로 둔다
@Table(
		name = "meeting_round",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_meeting_round_meeting_id_seq",
				columnNames = {"meeting_id", "seq"}),
		// 투표 마감 스케줄러가 주기적으로 찾는 조건
		indexes = @Index(
				name = "idx_meeting_round_place_status_place_vote_deadline",
				columnList = "place_status, place_vote_deadline"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	// 1부터 시작하는 차수 순서
	@Column(nullable = false)
	private int seq;

	// 차수 이름(예: "밥", "볼링")
	@Column(nullable = false, length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "place_status", nullable = false, length = 20)
	private PlaceStatus placeStatus;

	@Column(name = "place_vote_deadline")
	private LocalDateTime placeVoteDeadline;

	// 마감 처리 결과 호스트 선택 대기로 넘어간 시각. 있으면 스케줄러가 다시 처리하지 않아 선택 요청이 반복되지 않는다
	private LocalDateTime placeVoteClosedAt;

	// 확정된 장소 후보(CONFIRMED일 때만 있음). PlaceCandidate.round와 순환 참조라 nullable로 둔다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "confirmed_candidate_id")
	private PlaceCandidate confirmedCandidate;

	@Version
	private Long version;

	private Round(Meeting meeting, int seq, String name) {
		this.meeting = meeting;
		this.seq = seq;
		this.name = name;
		this.placeStatus = PlaceStatus.UNDECIDED;
	}

	/**
	 * 모임에 차수를 만든다. 장소 상태는 미정(UNDECIDED)으로 시작한다.
	 */
	public static Round create(Meeting meeting, int seq, String name) {
		return new Round(meeting, seq, name);
	}

	/**
	 * 투표 없이 장소를 바로 확정한다(UNDECIDED → CONFIRMED).
	 */
	public void confirmDirectly(PlaceCandidate candidate) {
		requireStatus(PlaceStatus.UNDECIDED);
		markConfirmed(candidate);
	}

	/**
	 * 장소 투표를 연다(UNDECIDED·CONFIRMED·호스트 선택 대기 → VOTING). 재투표면 이전 확정과 마감 처리 표식을 지운다.
	 * 마감 전 투표 중인 차수는 다시 열 수 없다.
	 */
	public void openVoting(LocalDateTime deadline) {
		if (!isAwaitingHostDecision()) {
			requireStatus(PlaceStatus.UNDECIDED, PlaceStatus.CONFIRMED);
		}
		this.placeStatus = PlaceStatus.VOTING;
		this.placeVoteDeadline = deadline;
		this.placeVoteClosedAt = null;
		this.confirmedCandidate = null;
	}

	/**
	 * 투표 중인 장소를 후보 하나로 확정한다(VOTING → CONFIRMED).
	 */
	public void confirm(PlaceCandidate candidate) {
		requireStatus(PlaceStatus.VOTING);
		markConfirmed(candidate);
	}

	/**
	 * 마감 처리 결과 자동 확정할 수 없어 호스트 선택 대기로 넘긴다(VOTING 유지).
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException VOTING이 아니면 ROUND_PLACE_INVALID_TRANSITION
	 */
	public void markVoteClosed(LocalDateTime now) {
		requireStatus(PlaceStatus.VOTING);
		this.placeVoteClosedAt = now;
	}

	/**
	 * 마감 처리가 끝나 호스트의 확정이나 재투표를 기다리는 중인지 본다.
	 */
	public boolean isAwaitingHostDecision() {
		return placeStatus == PlaceStatus.VOTING && placeVoteClosedAt != null;
	}

	/**
	 * 마감이 지났고 아직 마감 처리를 하지 않은 투표인지 본다. 마감 정각은 아직 열려 있다(requireVoteOpen과 같은 경계).
	 */
	public boolean isVoteCloseDue(LocalDateTime now) {
		return placeStatus == PlaceStatus.VOTING && placeVoteClosedAt == null && now.isAfter(placeVoteDeadline);
	}

	/**
	 * 확정된 장소를 돌려준다. 확정 전이면 null이다.
	 */
	public Place confirmedPlace() {
		return confirmedCandidate == null ? null : confirmedCandidate.getPlace();
	}

	/**
	 * 장소 미정 차수인지 검사한다(바로 확정 전에 후보를 저장하기 전 확인용).
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException UNDECIDED가 아니면 ROUND_PLACE_INVALID_TRANSITION
	 */
	public void requireUndecided() {
		requireStatus(PlaceStatus.UNDECIDED);
	}

	/**
	 * 투표 중인 차수인지 검사한다(후보 추가는 투표 중에만 된다).
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException VOTING이 아니면 ROUND_PLACE_INVALID_TRANSITION
	 */
	public void requireVoting() {
		requireStatus(PlaceStatus.VOTING);
	}

	/**
	 * 지금 표를 바꿀 수 있는지 검사한다. 마감 정각까지는 열려 있다.
	 * 마감이 지나면 VOTING이어도 막아서 마감 처리 직전이나 동점 대기 중에 결과가 바뀌지 않게 한다.
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException VOTING이 아니면 ROUND_PLACE_INVALID_TRANSITION,
	 *         마감이 지났으면 PLACE_VOTE_DEADLINE_PASSED
	 */
	public void requireVoteOpen(LocalDateTime now) {
		requireVoting();
		if (now.isAfter(placeVoteDeadline)) {
			throw new BusinessException(ErrorCode.PLACE_VOTE_DEADLINE_PASSED);
		}
	}

	private void markConfirmed(PlaceCandidate candidate) {
		// 다른 차수 후보가 넘어오는 건 호출 쪽 버그라 API 에러 코드가 아닌 예외로 막는다
		if (!candidate.belongsTo(this)) {
			throw new IllegalArgumentException("다른 차수의 후보로 확정할 수 없습니다.");
		}
		this.placeStatus = PlaceStatus.CONFIRMED;
		this.confirmedCandidate = candidate;
	}

	private void requireStatus(PlaceStatus... allowed) {
		if (Arrays.stream(allowed).noneMatch(status -> status == placeStatus)) {
			throw new BusinessException(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		}
	}
}
