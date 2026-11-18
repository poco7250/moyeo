package com.poco7250.moyeo.boardgame.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
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
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차수에서 한 보드게임 승패 기록(PRD R-7). 게임명·참가자 전원의 순위·꼴등(전액 부담자)을 남기고 벌칙·정산과는 무관하다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardGameRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false, updatable = false)
	private Round round;

	@Column(nullable = false, length = 50)
	private String gameName;

	// 기록한 회원 id. 수정·삭제 권한 검사에 쓴다
	@Column(nullable = false, updatable = false)
	private Long recordedBy;

	// 참가자 순위(순위 → 회원 id 순으로 보관)
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "board_game_rank", joinColumns = @JoinColumn(name = "board_game_record_id"))
	@OrderColumn(name = "display_order")
	private List<BoardGameRank> ranks = new ArrayList<>();

	// 꼴등(전액 부담자) 회원 id. 순위와 별개로 지정한다. 꼴등 지정 도입(2026-11-18) 전 기록은 null
	private Long loserMemberId;

	@Version
	private Long version;

	private BoardGameRecord(Round round, String gameName, Long recordedBy, List<BoardGameRank> ranks,
			Long loserMemberId) {
		this.round = round;
		this.recordedBy = recordedBy;
		apply(gameName, ranks, loserMemberId);
	}

	/**
	 * 새 보드게임 기록을 만든다.
	 *
	 * @throws IllegalArgumentException 꼴등이 참가자가 아니면(요청 DTO가 먼저 막으므로 호출 쪽 버그)
	 */
	public static BoardGameRecord create(Round round, String gameName, Long recordedBy, List<BoardGameRank> ranks,
			Long loserMemberId) {
		return new BoardGameRecord(round, gameName, recordedBy, ranks, loserMemberId);
	}

	/**
	 * 게임명·순위 목록·꼴등을 통째로 바꾼다.
	 *
	 * @throws IllegalArgumentException 꼴등이 참가자가 아니면(요청 DTO가 먼저 막으므로 호출 쪽 버그)
	 */
	public void update(String gameName, List<BoardGameRank> ranks, Long loserMemberId) {
		apply(gameName, ranks, loserMemberId);
	}

	/**
	 * 회원이 이 기록을 고치거나 지울 수 있는지(기록한 사람 또는 모임 호스트) 돌려준다.
	 */
	public boolean isEditableBy(Long memberId, Meeting meeting) {
		return Objects.equals(recordedBy, memberId) || meeting.isHost(memberId);
	}

	private void apply(String gameName, List<BoardGameRank> ranks, Long loserMemberId) {
		if (ranks.stream().noneMatch(rank -> rank.getMemberId().equals(loserMemberId))) {
			throw new IllegalArgumentException("꼴등은 참가자 중 한 명이어야 합니다: " + loserMemberId);
		}
		this.loserMemberId = loserMemberId;
		this.gameName = gameName.strip();
		this.ranks.clear();
		this.ranks.addAll(ranks.stream().sorted(BoardGameRank.DISPLAY_ORDER).toList());
	}
}
