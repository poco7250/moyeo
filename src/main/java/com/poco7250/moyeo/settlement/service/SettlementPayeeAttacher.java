package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.group.service.GroupMemberPaymentService;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정산 응답의 송금 안내 받는 사람(toMember) 송금 정보를 붙인다(PRD S-7, 2026-11-15 결정).
 * 보내기만 하는 사람·송금 정보가 없는 사람은 넣지 않는다. 리비전 이력·실시간 이벤트에는 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementPayeeAttacher {

	private final GroupMemberPaymentService paymentService;

	/**
	 * 응답의 송금 안내 받는 사람 송금 정보를 붙인 응답을 돌려준다.
	 */
	public SettlementResponse attach(Long groupId, SettlementResponse response) {
		Set<Long> payeeIds = response.transfers().stream()
				.map(SettlementTransferResponse::toMemberId)
				.collect(Collectors.toSet());
		return response.withPayees(paymentService.findPayees(groupId, payeeIds));
	}
}
