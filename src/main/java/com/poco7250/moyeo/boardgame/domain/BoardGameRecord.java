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
 * 차수에서 한 보드게임 승패 기록(PRD R-7). 게임명과 참가자 전원의 순위만 남기고 벌칙·정산과는 무관하다.
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

	@Version
	private Long version;

	private BoardGameRecord(Round round, String gameName, Long recordedBy, List<BoardGameRank> ranks) {
		this.round = round;
		this.recordedBy = recordedBy;
		apply(gameName, ranks);
	}

	/**
	 * 새 보드게임 기록을 만든다.
	 */
	public static BoardGameRecord create(Round round, String gameName, Long recordedBy, List<BoardGameRank> ranks) {
		return new BoardGameRecord(round, gameName, recordedBy, ranks);
	}

	/**
	 * 게임명과 순위 목록을 통째로 바꾼다.
	 */
	public void update(String gameName, List<BoardGameRank> ranks) {
		apply(gameName, ranks);
	}

	/**
	 * 회원이 이 기록을 고치거나 지울 수 있는지(기록한 사람 또는 모임 호스트) 돌려준다.
	 */
	public boolean isEditableBy(Long memberId, Meeting meeting) {
		return Objects.equals(recordedBy, memberId) || meeting.isHost(memberId);
	}

	private void apply(String gameName, List<BoardGameRank> ranks) {
		this.gameName = gameName.strip();
		this.ranks.clear();
		this.ranks.addAll(ranks.stream().sorted(BoardGameRank.DISPLAY_ORDER).toList());
	}
}
