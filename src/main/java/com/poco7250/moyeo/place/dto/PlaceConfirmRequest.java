package com.poco7250.moyeo.place.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 호스트 수동 확정 요청. 투표 중인 차수의 후보 하나를 고른다.
 */
public record PlaceConfirmRequest(@NotNull(message = "확정할 후보를 골라 주세요.") Long candidateId) {
}
