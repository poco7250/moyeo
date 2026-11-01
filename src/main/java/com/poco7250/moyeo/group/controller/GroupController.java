package com.poco7250.moyeo.group.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.group.dto.GroupCreateRequest;
import com.poco7250.moyeo.group.dto.GroupCreateResponse;
import com.poco7250.moyeo.group.dto.GroupResponse;
import com.poco7250.moyeo.group.dto.InviteLinkResponse;
import com.poco7250.moyeo.group.service.GroupService;
import com.poco7250.moyeo.group.service.InviteLinkService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 API. 전부 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

	private final GroupService groupService;
	private final InviteLinkService inviteLinkService;

	/**
	 * 그룹을 만들고 초대 링크를 함께 돌려준다.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<GroupCreateResponse> create(
			@LoginMember Long memberId, @Valid @RequestBody GroupCreateRequest request) {
		return ApiResponse.ok(groupService.create(memberId, request));
	}

	/**
	 * 내가 속한 그룹 목록을 돌려준다.
	 */
	@GetMapping
	public ApiResponse<List<GroupResponse>> myGroups(@LoginMember Long memberId) {
		return ApiResponse.ok(groupService.getMyGroups(memberId));
	}

	/**
	 * 초대 링크를 재발급한다. 기존 링크는 폐기된다.
	 */
	@PostMapping("/{groupId}/invite-links")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<InviteLinkResponse> reissueInviteLink(
			@LoginMember Long memberId, @PathVariable Long groupId) {
		return ApiResponse.ok(inviteLinkService.reissue(groupId, memberId));
	}
}
