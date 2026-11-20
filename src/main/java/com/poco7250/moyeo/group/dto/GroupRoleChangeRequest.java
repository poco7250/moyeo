package com.poco7250.moyeo.group.dto;

import com.poco7250.moyeo.group.domain.GroupRole;
import jakarta.validation.constraints.NotNull;

/**
 * 멤버 역할 변경 요청(G-4, 방장). TREASURER 또는 MEMBER만 받는다(OWNER는 양도 API로).
 */
public record GroupRoleChangeRequest(@NotNull(message = "바꿀 역할을 골라 주세요.") GroupRole role) {
}
