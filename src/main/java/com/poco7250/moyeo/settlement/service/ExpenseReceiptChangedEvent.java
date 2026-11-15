package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptChangedResponse;

/**
 * 지출 영수증 사진이 올라가거나 바뀌거나 지워졌음을 모임 토픽에 알리는 이벤트(PRD S-1 확장). 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record ExpenseReceiptChangedEvent(Long meetingId, ExpenseReceiptChangedResponse data) implements RealtimeEvent {

	public static final String TYPE = "EXPENSE_RECEIPT_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
