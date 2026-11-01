package com.poco7250.moyeo.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 그룹 안 내 프로필 변경 요청. 둘 다 보내는 전체 교체이고, 이모지를 비우면 기본 아이콘으로 돌아간다.
 * 길이 제한은 엔티티 컬럼 길이(닉네임 50, 이모지 16)와 맞춘다.
 */
public record GroupMemberUpdateRequest(
		@NotBlank(message = "닉네임을 입력해 주세요.")
		@Size(max = 50, message = "닉네임은 50자 이하로 입력해 주세요.")
		String nickname,

		@Size(max = 16, message = "이모지는 16자 이하로 입력해 주세요.")
		String emoji) {
}
