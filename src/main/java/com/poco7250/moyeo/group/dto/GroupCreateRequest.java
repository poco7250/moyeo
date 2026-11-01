package com.poco7250.moyeo.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 그룹 생성 요청. 이름 길이 제한은 엔티티 컬럼 길이(50)와 맞춘다.
 */
public record GroupCreateRequest(
		@NotBlank(message = "그룹 이름을 입력해 주세요.")
		@Size(max = 50, message = "그룹 이름은 50자 이하로 입력해 주세요.")
		String name) {
}
