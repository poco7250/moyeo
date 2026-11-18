package com.poco7250.moyeo.boardgame.dto;

import com.poco7250.moyeo.boardgame.domain.BoardGameRank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 보드게임 기록 생성·수정 요청. 참가자는 차수 참석자 중에서 고르고 전원에게 순위를 준다.
 *
 * @param ranks 참가자별 순위(2~50명)
 */
public record BoardGameRecordRequest(
		@NotBlank @Size(max = 50) String gameName,
		@NotNull @Size(min = 2, max = 50) List<@NotNull @Valid BoardGameRankRequest> ranks) {

	/**
	 * 같은 회원을 두 번 넣지 않았는지 검사한다(필드 에러 이름: membersDistinct).
	 */
	@AssertTrue(message = "같은 참가자를 두 번 넣을 수 없습니다.")
	public boolean isMembersDistinct() {
		if (ranks == null) {
			return true;
		}
		List<Long> memberIds = memberIds();
		return new HashSet<>(memberIds).size() == memberIds.size();
	}

	/**
	 * 순위가 참가자 수를 넘지 않는지 검사한다(필드 에러 이름: rankWithinCount).
	 */
	@AssertTrue(message = "순위는 참가자 수보다 클 수 없습니다.")
	public boolean isRankWithinCount() {
		return ranks == null || ranks.stream()
				.filter(Objects::nonNull)
				.map(BoardGameRankRequest::rank)
				.filter(Objects::nonNull)
				.allMatch(rank -> rank <= ranks.size());
	}

	/**
	 * 참가자 회원 id 목록을 돌려준다.
	 */
	public List<Long> memberIds() {
		return ranks.stream().filter(Objects::nonNull).map(BoardGameRankRequest::memberId).toList();
	}

	/**
	 * 요청 순위를 도메인 값으로 바꾼다. 검증을 통과한 요청에서만 부른다.
	 */
	public List<BoardGameRank> toRanks() {
		return ranks.stream().map(rank -> BoardGameRank.of(rank.memberId(), rank.rank())).toList();
	}
}
