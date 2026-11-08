package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 금액 영향 쓰기의 정산 직렬화와 REOPENED 전이를 맡는다.
 * 호출자 트랜잭션 안에서만 의미가 있어, 트랜잭션 없이 부르면 잠금이 사라지는 대신 바로 실패하게 MANDATORY로 둔다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SettlementGuard {

	private final SettlementRepository settlementRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 금액 영향 쓰기를 직렬화하도록 정산을 잠근다. 커밋 시 version이 올라가 늦은 쪽이 충돌한다.
	 *
	 * @return 잠근 정산(확정 검사 등에 쓴다)
	 */
	public Settlement lockForAmountChange(Long meetingId) {
		return settlementRepository.findByMeetingIdForUpdate(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
	}

	/**
	 * 금액에 영향은 없지만 확정 검사와 경합하면 안 되는 쓰기(금액형 게임 취소)를 직렬화한다. 상태는 바꾸지 않는다.
	 */
	public void lockOnly(Long meetingId) {
		lockForAmountChange(meetingId);
	}

	/**
	 * 금액 영향 변경을 반영한다. CONFIRMED면 REOPENED로 바꾸고 커밋 후 "재확정 필요" 이벤트가 나가게 한다.
	 * 잠금을 빠뜨린 호출자도 직렬화에서 새지 않도록 여기서 다시 잠금 조회를 거친다.
	 *
	 * @param actorId 변경한 회원. 스케줄러가 일으킨 변경이면 null
	 */
	public void markAmountChanged(Long meetingId, Long actorId) {
		if (lockForAmountChange(meetingId).reopen()) {
			eventPublisher.publishEvent(
					new SettlementReopenedEvent(meetingId, new SettlementReopenedResponse(actorId)));
		}
	}
}
