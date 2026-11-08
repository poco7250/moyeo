package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;

/**
 * 확정된 정산이 금액 영향 변경으로 재확정 필요 상태가 됐음을 모임 토픽에 알리는 이벤트.
 */
public record SettlementReopenedEvent(Long meetingId, SettlementReopenedResponse data) implements RealtimeEvent {

	public static final String TYPE = "SETTLEMENT_REOPENED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
