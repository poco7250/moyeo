package com.poco7250.moyeo.group.dto;

import java.time.LocalDateTime;

/**
 * 초대 진입 화면("OO 모임에 초대받았어요")용 응답. 비로그인에게도 주므로 그룹 id 같은 내부 값은 넣지 않는다.
 */
public record InvitePreviewResponse(String groupName, long memberCount, LocalDateTime expiresAt) {
}
