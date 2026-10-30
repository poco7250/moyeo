package com.poco7250.moyeo.member.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 API.
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	/**
	 * 로그인한 회원 본인의 정보(닉네임·프로필 이미지)를 돌려준다. 인증이 없으면 필터 단계에서 401이 나간다.
	 */
	@GetMapping("/me")
	public ApiResponse<MemberResponse> me(@LoginMember Long memberId) {
		return ApiResponse.ok(memberService.getMe(memberId));
	}
}
