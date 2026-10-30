package com.poco7250.moyeo.auth.security;

import com.poco7250.moyeo.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * SecurityIntegrationTest용 테스트 전용 컨트롤러. 인증 필요 경로와 공개 경로를 하나씩 둔다.
 */
@RestController
class SecurityTestController {

	/**
	 * 로그인한 회원 ID를 그대로 돌려준다(인증 필요).
	 */
	@GetMapping("/test/security/me")
	ApiResponse<Long> me(@LoginMember Long memberId) {
		return ApiResponse.ok(memberId);
	}

	/**
	 * 공개 경로(초대 링크 미리보기) 대역. 초대 코드를 그대로 돌려준다.
	 */
	@GetMapping("/api/invites/{code}")
	ApiResponse<String> invite(@PathVariable String code) {
		return ApiResponse.ok(code);
	}
}
