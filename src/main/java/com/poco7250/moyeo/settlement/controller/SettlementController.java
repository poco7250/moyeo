package com.poco7250.moyeo.settlement.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.settlement.dto.SettlementConfirmRequest;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementHistoryService;
import com.poco7250.moyeo.settlement.service.SettlementQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 조회·확정·이력 API. 조회·이력은 그룹 멤버, 확정·재확정은 모임 호스트만 할 수 있다.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/settlement")
@RequiredArgsConstructor
public class SettlementController {

	private final SettlementQueryService settlementQueryService;
	private final SettlementConfirmService settlementConfirmService;
	private final SettlementHistoryService settlementHistoryService;

	/**
	 * 모임의 정산을 돌려준다.
	 */
	@GetMapping
	public ApiResponse<SettlementResponse> get(@LoginMember Long memberId, @PathVariable Long meetingId) {
		return ApiResponse.ok(settlementQueryService.get(meetingId, memberId));
	}

	/**
	 * 확정된 리비전을 번호 순으로 돌려준다(직전 리비전 대비 변동액 포함). 확정 전이면 빈 목록이다.
	 */
	@GetMapping("/revisions")
	public ApiResponse<List<SettlementRevisionResponse>> revisions(@LoginMember Long memberId,
			@PathVariable Long meetingId) {
		return ApiResponse.ok(settlementHistoryService.revisions(meetingId, memberId));
	}

	/**
	 * 정산을 확정하거나 재확정한다. 본문을 생략하면 경고를 확인하지 않은 것으로 본다.
	 */
	@PostMapping("/confirm")
	public ApiResponse<SettlementResponse> confirm(@LoginMember Long memberId, @PathVariable Long meetingId,
			@RequestBody(required = false) SettlementConfirmRequest request) {
		boolean acknowledged = request != null && request.acknowledged();
		return ApiResponse.ok(settlementConfirmService.confirm(meetingId, memberId, acknowledged));
	}
}
