package com.poco7250.moyeo.place.dto;

/**
 * 룰렛을 돌린 결과. 확정된 차수 장소 상태와 이번 룰렛 기록을 함께 준다.
 */
public record PlaceRouletteResultResponse(RoundPlaceResponse place, PlaceRouletteResponse roulette) {
}
