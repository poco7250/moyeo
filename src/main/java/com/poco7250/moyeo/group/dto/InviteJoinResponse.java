package com.poco7250.moyeo.group.dto;

/**
 * 초대 링크 가입 응답. joined가 false면 이미 멤버였다는 뜻이고, 프론트는 둘 다 그룹 홈으로 보낸다.
 */
public record InviteJoinResponse(Long groupId, boolean joined) {
}
