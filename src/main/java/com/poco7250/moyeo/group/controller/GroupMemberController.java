package com.poco7250.moyeo.group.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.group.dto.GroupMemberResponse;
import com.poco7250.moyeo.group.dto.GroupMemberUpdateRequest;
import com.poco7250.moyeo.group.service.GroupMemberService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 멤버 API. 전부 로그인이 필요하고 그룹 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/members")
@RequiredArgsConstructor
public class GroupMemberController {

	private final GroupMemberService groupMemberService;

	/**
	 * 그룹 멤버 목록(그룹 닉네임·이모지)을 준다.
	 */
	@GetMapping
	public ApiResponse<List<GroupMemberResponse>> members(@LoginMember Long memberId, @PathVariable Long groupId) {
		return ApiResponse.ok(groupMemberService.getMembers(groupId, memberId));
	}

	/**
	 * 그룹 안 내 닉네임·이모지를 바꾼다.
	 */
	@PatchMapping("/me")
	public ApiResponse<GroupMemberResponse> updateMe(@LoginMember Long memberId, @PathVariable Long groupId,
			@Valid @RequestBody GroupMemberUpdateRequest request) {
		return ApiResponse.ok(groupMemberService.updateMyProfile(groupId, memberId, request));
	}
}
