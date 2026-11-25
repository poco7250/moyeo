package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

/**
 * 게임 생성 요청. 참가자는 모임 참석자(아무 차수에든 ATTEND)·모임 게스트 중에서 고른 회원 id 목록이다.
 *
 * @param penaltyKind 벌칙 종류. 순서 정하기(ORDER)는 NONE만 된다
 * @param penaltyAmount 금액형(MONEY) 벌칙자 1인이 낼 금액(원, 1원~1억 원). 금액형만 입력한다
 * @param penaltyCount 뽑을 벌칙자 수. 없으면 1명. 순서 정하기(ORDER)는 입력하지 않는다
 */
public record GameCreateRequest(
		@NotNull GameType type,
		@NotNull PenaltyKind penaltyKind,
		@Min(1) @Max(MAX_PENALTY_AMOUNT) Long penaltyAmount,
		@Size(max = 100) String penaltyNote,
		@NotNull @Size(min = 2, max = 50) List<@NotNull Long> participantMemberIds,
		Integer penaltyCount) {

	public static final long MAX_PENALTY_AMOUNT = 100_000_000L;

	private static final int DEFAULT_PENALTY_COUNT = 1;

	/**
	 * 입력이 없을 때 기본값을 적용한 벌칙자 수를 돌려준다.
	 */
	public int resolvedPenaltyCount() {
		if (isOrderType()) {
			return 0;
		}
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
		if (isOrderType()) {
			return true;
		}
		int count = resolvedPenaltyCount();
		return count >= 1 && (participantMemberIds == null || count < participantMemberIds.size());
	}

	/**
	 * 금액형이면 금액이 있고, 아니면 금액이 없는지 검사한다(필드 에러 이름: penaltyAmountMatchesKind).
	 */
	@AssertTrue(message = "금액 벌칙이면 금액을 입력하고, 아니면 금액을 비워 주세요.")
	public boolean isPenaltyAmountMatchesKind() {
		return penaltyKind == null || (penaltyKind == PenaltyKind.MONEY) == (penaltyAmount != null);
	}

	/**
	 * 순서 정하기 게임이면 벌칙 없이(NONE, 벌칙자 수 미입력) 만드는지 검사한다(필드 에러 이름: orderPenaltyNone).
	 */
	@AssertTrue(message = "순서 정하기는 벌칙 없이(벌칙 종류 없음, 벌칙자 수 미입력) 만들어야 합니다.")
	public boolean isOrderPenaltyNone() {
		return !isOrderType() || (penaltyKind == PenaltyKind.NONE && penaltyCount == null);
	}

	private boolean isOrderType() {
		return type != null && type.isOrder();
	}
}
