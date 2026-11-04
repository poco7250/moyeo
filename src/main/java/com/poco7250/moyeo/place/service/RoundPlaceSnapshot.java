package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 차수의 현재 장소 상태를 읽어 응답을 만들고 변경 이벤트를 발행한다. 호출하는 Service의 트랜잭션 안에서 쓴다.
 */
@Component
@RequiredArgsConstructor
class RoundPlaceSnapshot {

	private final PlaceCandidateRepository placeCandidateRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 차수와 후보 목록으로 응답을 만들고 모임 토픽에 PLACE_CHANGED를 발행한 뒤 응답을 돌려준다.
	 */
	RoundPlaceResponse publish(Round round) {
		RoundPlaceResponse response = RoundPlaceResponse.of(
				round, placeCandidateRepository.findAllByRoundIdOrderById(round.getId()));
		eventPublisher.publishEvent(new PlaceChangedEvent(round.getMeeting().getId(), response));
		return response;
	}
}
