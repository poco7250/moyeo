package com.poco7250.moyeo.meeting.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 날짜 확정 요청(호스트).
 */
public record DateConfirmRequest(@NotNull(message = "확정할 날짜 후보를 골라 주세요.") Long candidateId) {
}
