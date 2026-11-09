package com.poco7250.moyeo.settlement.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareAdjustRequest;
import com.poco7250.moyeo.settlement.service.ExpenseService;
import com.poco7250.moyeo.settlement.service.ExpenseShareService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지출·분배 조정 API. 전부 로그인이 필요하고, 모임이 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExpenseController {

	private final ExpenseService expenseService;
	private final ExpenseShareService expenseShareService;

	/**
	 * 차수에 지출을 등록한다.
	 */
	@PostMapping("/rounds/{roundId}/expenses")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ExpenseResponse> create(@LoginMember Long memberId, @PathVariable Long roundId,
			@Valid @RequestBody ExpenseRequest request) {
		return ApiResponse.ok(expenseService.create(roundId, memberId, request));
	}

	/**
	 * 지출을 수정한다.
	 */
	@PutMapping("/expenses/{expenseId}")
	public ApiResponse<ExpenseResponse> update(@LoginMember Long memberId, @PathVariable Long expenseId,
			@Valid @RequestBody ExpenseRequest request) {
		return ApiResponse.ok(expenseService.update(expenseId, memberId, request));
	}

	/**
	 * 지출을 삭제한다.
	 */
	@DeleteMapping("/expenses/{expenseId}")
	public ApiResponse<Void> delete(@LoginMember Long memberId, @PathVariable Long expenseId) {
		expenseService.delete(expenseId, memberId);
		return ApiResponse.ok();
	}

	/**
	 * 지출의 분배 조정을 전체 교체한다.
	 */
	@PutMapping("/expenses/{expenseId}/shares")
	public ApiResponse<ExpenseResponse> adjustShares(@LoginMember Long memberId, @PathVariable Long expenseId,
			@Valid @RequestBody ExpenseShareAdjustRequest request) {
		return ApiResponse.ok(expenseShareService.adjust(expenseId, memberId, request));
	}

	/**
	 * 모임의 지출 목록을 돌려준다.
	 */
	@GetMapping("/meetings/{meetingId}/expenses")
	public ApiResponse<List<ExpenseResponse>> list(@LoginMember Long memberId, @PathVariable Long meetingId) {
		return ApiResponse.ok(expenseService.list(meetingId, memberId));
	}
}
