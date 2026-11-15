package com.poco7250.moyeo.group.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.group.dto.PaymentInfoRequest;
import com.poco7250.moyeo.group.dto.PaymentInfoResponse;
import com.poco7250.moyeo.group.service.GroupMemberPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 안 내 송금 정보(계좌·송금 링크) API(PRD G-5·S-7). 전부 로그인이 필요하고 본인 정보만 다룬다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/members/me/payment")
@RequiredArgsConstructor
public class GroupMemberPaymentController {

	private final GroupMemberPaymentService paymentService;

	/**
	 * 내 송금 정보를 준다. 없으면 data가 null이다.
	 */
	@GetMapping
	public ApiResponse<PaymentInfoResponse> get(@LoginMember Long memberId, @PathVariable Long groupId) {
		return ApiResponse.ok(paymentService.getMine(groupId, memberId));
	}

	/**
	 * 내 송금 정보를 통째로 저장한다.
	 */
	@PutMapping
	public ApiResponse<PaymentInfoResponse> replace(@LoginMember Long memberId, @PathVariable Long groupId,
			@Valid @RequestBody PaymentInfoRequest request) {
		return ApiResponse.ok(paymentService.replaceMine(groupId, memberId, request));
	}

	/**
	 * 내 송금 정보를 지운다.
	 */
	@DeleteMapping
	public ApiResponse<Void> delete(@LoginMember Long memberId, @PathVariable Long groupId) {
		paymentService.deleteMine(groupId, memberId);
		return ApiResponse.ok();
	}
}
