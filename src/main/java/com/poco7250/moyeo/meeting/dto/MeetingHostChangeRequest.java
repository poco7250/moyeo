package com.poco7250.moyeo.meeting.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 모임 호스트 위임 요청(G-4). 새 호스트가 될 그룹 멤버의 회원 id를 받는다.
 */
public record MeetingHostChangeRequest(@NotNull(message = "새 호스트를 골라 주세요.") Long memberId) {
}
