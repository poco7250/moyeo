package com.poco7250.moyeo.group.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import com.poco7250.moyeo.group.dto.InvitePreviewResponse;
import com.poco7250.moyeo.group.service.InviteJoinService;
import com.poco7250.moyeo.group.service.InviteLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 링크 진입 API. 미리보기는 비로그인도 쓰고(SecurityConfig 공개 경로), 가입은 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/invites/{token}")
@RequiredArgsConstructor
public class InviteController {

	private final InviteLinkService inviteLinkService;
	private final InviteJoinService inviteJoinService;

	/**
	 * 초대 진입 화면용 그룹 정보를 준다.
	 */
	@GetMapping
	public ApiResponse<InvitePreviewResponse> preview(@PathVariable String token) {
		return ApiResponse.ok(inviteLinkService.getPreview(token));
	}

	/**
	 * 초대 링크로 그룹에 가입한다. 이미 멤버여도 200으로 그룹 id를 준다.
	 */
	@PostMapping("/join")
	public ApiResponse<InviteJoinResponse> join(@LoginMember Long memberId, @PathVariable String token) {
		return ApiResponse.ok(inviteJoinService.join(token, memberId));
	}
}
