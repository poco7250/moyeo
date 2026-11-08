package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 금액 영향 변경을 변경 로그에 남긴다. 변경과 함께 커밋·롤백되도록 호출자 트랜잭션 안에서만 동작한다.
 */
@Component
@RequiredArgsConstructor
public class ChangeLogRecorder {

	private final SettlementRepository settlementRepository;
	private final ChangeLogRepository changeLogRepository;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	/**
	 * 변경 로그 한 건을 저장한다. 변경 후 정산 상태를 함께 남기므로 {@code markAmountChanged} 뒤에 호출한다.
	 *
	 * @param actorId 변경한 회원. 스케줄러가 일으킨 변경이면 null
	 * @param before 변경 전 값. 생성이면 null
	 * @param after 변경 후 값. 삭제면 null
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(Long meetingId, Long actorId, ChangeLogEntityType entityType, Long entityId,
			ChangeType changeType, ChangeLogSnapshot before, ChangeLogSnapshot after) {
		Settlement settlement = settlementRepository.findByMeetingId(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
		changeLogRepository.save(ChangeLog.record(settlement.getMeeting(), actorId, LocalDateTime.now(clock),
				entityType, entityId, changeType, toJson(before), toJson(after), settlement.getStatus()));
	}

	private String toJson(ChangeLogSnapshot snapshot) {
		return snapshot == null ? null : jsonMapper.writeValueAsString(snapshot);
	}
}
