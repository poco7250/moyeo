package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.settlement.dto.SettlementReconfirmedResponse;

/**
 * 재확정 필요였던 정산이 다시 확정됐음을 모임 토픽에 알리는 이벤트. 최초 확정에는 보내지 않는다.
 */
public record SettlementReconfirmedEvent(Long meetingId, SettlementReconfirmedResponse data)
		implements RealtimeEvent {

	public static final String TYPE = "SETTLEMENT_RECONFIRMED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
