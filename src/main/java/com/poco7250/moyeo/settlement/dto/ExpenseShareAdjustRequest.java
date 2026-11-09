package com.poco7250.moyeo.settlement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

/**
 * 지출의 분배 조정 전체 교체 요청. 목록에 없는 회원의 조정은 지워지고, 빈 목록은 조정을 모두 해제한다.
 */
public record ExpenseShareAdjustRequest(
		@NotNull(message = "조정 목록을 보내 주세요.")
		List<@NotNull @Valid ExpenseShareItem> shares) {

	/**
	 * 같은 회원을 두 번 보내지 않았는지 검사한다(필드 에러 이름: memberIdsUnique).
	 */
	@AssertTrue(message = "같은 멤버를 두 번 조정할 수 없습니다.")
	public boolean isMemberIdsUnique() {
		if (shares == null) {
			return true;
		}
		List<Long> ids = shares.stream().filter(Objects::nonNull).map(ExpenseShareItem::memberId)
				.filter(Objects::nonNull).toList();
		return ids.stream().distinct().count() == ids.size();
	}
}
