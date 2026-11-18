package com.poco7250.moyeo.boardgame.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보드게임 기록의 참가자 한 명 순위. 같은 순위(공동 순위)를 허용한다.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardGameRank {

	// 순위 오름차순, 같은 순위면 회원 id 오름차순
	public static final Comparator<BoardGameRank> DISPLAY_ORDER = Comparator.comparingInt(BoardGameRank::getRankNo)
			.thenComparing(BoardGameRank::getMemberId);

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "rank_no", nullable = false)
	private int rankNo;

	private BoardGameRank(Long memberId, int rankNo) {
		this.memberId = memberId;
		this.rankNo = rankNo;
	}

	/**
	 * 회원의 순위를 만든다. 순위 범위 검사는 요청 DTO가 한다.
	 */
	public static BoardGameRank of(Long memberId, int rankNo) {
		return new BoardGameRank(memberId, rankNo);
	}
}
