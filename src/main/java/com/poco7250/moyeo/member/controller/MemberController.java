package com.poco7250.moyeo.member.controller;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.member.dto.MemberJoinRequest;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원 화면 컨트롤러.
 */
@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

	private static final String JOIN_FORM_VIEW = "member/joinForm";

	private final MemberService memberService;

	/**
	 * 회원가입 폼을 보여준다.
	 */
	@GetMapping("/join")
	public String joinForm(Model model) {
		model.addAttribute("joinRequest", new MemberJoinRequest());
		return JOIN_FORM_VIEW;
	}

	/**
	 * 회원가입을 처리한다. 실패하면 입력값과 에러를 담아 폼을 다시 보여주고,
	 * 성공하면 새로고침 시 중복 제출되지 않도록 완료 페이지로 리다이렉트한다(PRG).
	 */
	@PostMapping("/join")
	public String join(@Valid @ModelAttribute("joinRequest") MemberJoinRequest request,
			BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (!request.isPasswordMatched()) {
			bindingResult.rejectValue("passwordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
		}
		if (bindingResult.hasErrors()) {
			return JOIN_FORM_VIEW;
		}

		try {
			MemberResponse member = memberService.join(request);
			redirectAttributes.addFlashAttribute("nickname", member.nickname());
			return "redirect:/members/join/complete";
		} catch (BusinessException e) {
			// 중복 같은 비즈니스 에러는 에러 페이지 대신 폼 상단에 보여준다
			bindingResult.reject(e.getErrorCode().name(), e.getMessage());
			return JOIN_FORM_VIEW;
		}
	}

	/**
	 * 회원가입 완료 페이지를 보여준다.
	 */
	@GetMapping("/join/complete")
	public String joinComplete() {
		return "member/joinComplete";
	}
}
