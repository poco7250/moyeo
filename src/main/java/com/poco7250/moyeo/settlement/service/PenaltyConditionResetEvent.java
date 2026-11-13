package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.settlement.dto.ExpenseDeleteResponse;

/**
 * 대상 지출이 삭제되어 벌칙 조건을 다시 입력해야 함을 모임 토픽에 알리는 이벤트(PRD S-1).
 */
public record PenaltyConditionResetEvent(Long meetingId, ExpenseDeleteResponse data) implements RealtimeEvent {

	public static final String TYPE = "PENALTY_CONDITION_RESET";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
