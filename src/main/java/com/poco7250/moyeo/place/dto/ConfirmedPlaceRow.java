package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.place.domain.Place;
import java.time.LocalDateTime;

/**
 * 지난 모임의 확정 장소 한 줄(차수 하나). 지난 장소 불러오기 조회의 프로젝션이다.
 */
public record ConfirmedPlaceRow(Place place, LocalDateTime startAt) {
}
