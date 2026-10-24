package com.poco7250.moyeo.global.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 페이지 컨트롤러.
 */
@Controller
public class HomeController {

	/**
	 * 메인 페이지를 보여준다.
	 */
	@GetMapping("/")
	public String home() {
		return "index";
	}
}
