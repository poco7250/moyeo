package com.poco7250.moyeo.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * 컨트롤러에서 처리하지 못한 예외를 공통 에러 페이지로 보낸다.
 * 404, 500 같은 나머지 예외는 Spring Boot 기본 에러 처리(templates/error/*.html)에 맡긴다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

	private static final String ERROR_VIEW = "error/common";

	/**
	 * 비즈니스 예외를 에러 코드의 상태와 메시지로 보여준다.
	 */
	@ExceptionHandler(BusinessException.class)
	public ModelAndView handleBusinessException(BusinessException e) {
		log.warn("비즈니스 예외: code={}, message={}", e.getErrorCode(), e.getMessage());
		return errorView(e.getErrorCode());
	}

	/**
	 * 동시 요청 등으로 DB 유니크 제약을 위반한 경우를 처리한다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ModelAndView handleDataIntegrityViolation(DataIntegrityViolationException e) {
		log.warn("데이터 무결성 위반: {}", e.getMostSpecificCause().getMessage());
		return errorView(ErrorCode.DUPLICATE_RESOURCE);
	}

	/**
	 * 에러 코드로 공통 에러 뷰를 만든다.
	 */
	private ModelAndView errorView(ErrorCode errorCode) {
		ModelAndView mav = new ModelAndView(ERROR_VIEW);
		mav.setStatus(errorCode.getStatus());
		mav.addObject("status", errorCode.getStatus().value());
		mav.addObject("message", errorCode.getMessage());
		return mav;
	}
}
