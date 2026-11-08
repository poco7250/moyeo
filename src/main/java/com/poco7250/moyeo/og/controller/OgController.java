package com.poco7250.moyeo.og.controller;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.og.service.OgService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 단톡방 공유 링크. 카카오톡 크롤러에는 OG 메타를 보여 주고, 사람은 SPA로 보낸다. 로그인이 필요 없다.
 */
@Controller
@RequestMapping("/share")
@RequiredArgsConstructor
public class OgController {

	private static final String SHARE_VIEW = "og/share";
	private static final String UNAVAILABLE_VIEW = "og/unavailable";

	private final OgService ogService;

	/**
	 * 모임 공유 링크 페이지를 돌려준다.
	 */
	@GetMapping("/meetings/{shareToken}")
	public String meeting(@PathVariable String shareToken, Model model) {
		model.addAttribute("og", ogService.meetingMeta(shareToken));
		return SHARE_VIEW;
	}

	/**
	 * 초대 공유 링크 페이지를 돌려준다.
	 */
	@GetMapping("/invites/{token}")
	public String invite(@PathVariable String token, Model model) {
		model.addAttribute("og", ogService.inviteMeta(token));
		return SHARE_VIEW;
	}

	/**
	 * 없거나 만료된 링크는 JSON 대신 안내 HTML을 준다. 상태 코드는 ErrorCode를 따른다(없음 404, 만료 410).
	 */
	@ExceptionHandler(BusinessException.class)
	public String unavailable(BusinessException exception, HttpServletResponse response) {
		response.setStatus(exception.getErrorCode().getStatus().value());
		return UNAVAILABLE_VIEW;
	}
}
