package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/**
 * 게임 생성 요청. 참가자는 차수 참석자 중에서 고른 회원 id 목록이다.
 *
 * @param penaltyCount 뽑을 벌칙자 수. 없으면 1명
 */
public record GameCreateRequest(
		@NotNull GameType type,
		@NotNull PenaltyKind penaltyKind,
		@Size(max = 100) String penaltyNote,
		@NotNull @Size(min = 2, max = 50) List<@NotNull Long> participantMemberIds,
		Integer penaltyCount) {

	private static final int DEFAULT_PENALTY_COUNT = 1;

	/**
	 * 입력이 없을 때 기본값을 적용한 벌칙자 수를 돌려준다.
	 */
	public int resolvedPenaltyCount() {
		return penaltyCount == null ? DEFAULT_PENALTY_COUNT : penaltyCount;
	}

	/**
	 * 같은 회원을 두 번 고르지 않았는지 검사한다(필드 에러 이름: participantsDistinct).
	 */
	@AssertTrue(message = "같은 참가자를 두 번 고를 수 없습니다.")
	public boolean isParticipantsDistinct() {
		return participantMemberIds == null
				|| new HashSet<>(participantMemberIds).size() == participantMemberIds.size();
	}

	/**
	 * 벌칙자 수가 1명 이상, 참가자 수 미만인지 검사한다(필드 에러 이름: penaltyCountValid).
	 */
	@AssertTrue(message = "벌칙자 수는 1명 이상, 참가자 수보다 적어야 합니다.")
	public boolean isPenaltyCountValid() {
		int count = resolvedPenaltyCount();
		return count >= 1 && (participantMemberIds == null || count < participantMemberIds.size());
	}
}
