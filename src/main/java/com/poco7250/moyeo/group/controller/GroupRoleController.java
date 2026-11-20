package com.poco7250.moyeo.group.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.dto.GroupRoleChangeRequest;
import com.poco7250.moyeo.group.dto.OwnerTransferRequest;
import com.poco7250.moyeo.group.service.GroupRoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 역할 API(G-4). 전부 로그인이 필요하고 방장만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class GroupRoleController {

	private final GroupRoleService groupRoleService;

	/**
	 * 멤버를 총무로 지정하거나 일반 멤버로 되돌린다.
	 */
	@PutMapping("/members/{targetMemberId}/role")
	public ApiResponse<GroupMemberResponse> changeRole(@LoginMember Long memberId, @PathVariable Long groupId,
			@PathVariable Long targetMemberId, @Valid @RequestBody GroupRoleChangeRequest request) {
		return ApiResponse.ok(groupRoleService.changeRole(groupId, memberId, targetMemberId, request.role()));
	}

	/**
	 * 방장 자리를 다른 멤버에게 넘기고 바뀐 두 멤버(이전 방장, 새 방장)를 돌려준다.
	 */
	@PostMapping("/owner-transfer")
	public ApiResponse<List<GroupMemberResponse>> transferOwner(@LoginMember Long memberId,
			@PathVariable Long groupId, @Valid @RequestBody OwnerTransferRequest request) {
		return ApiResponse.ok(groupRoleService.transferOwner(groupId, memberId, request.memberId()));
	}
}
