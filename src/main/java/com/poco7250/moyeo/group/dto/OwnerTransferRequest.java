package com.poco7250.moyeo.group.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 방장 양도 요청(G-4, 방장). 새 방장이 될 그룹 멤버의 회원 id를 받는다.
 */
public record OwnerTransferRequest(@NotNull(message = "새 방장을 골라 주세요.") Long memberId) {
}
