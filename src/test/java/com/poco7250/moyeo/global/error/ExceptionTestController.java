package com.poco7250.moyeo.global.error;

import com.poco7250.moyeo.global.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalExceptionHandler 검증용 테스트 전용 컨트롤러. 엔드포인트마다 특정 예외를 일으킨다.
 */
@RestController
@RequestMapping("/test/exceptions")
@RequiredArgsConstructor
class ExceptionTestController {

	static final String SENSITIVE_MESSAGE = "민감한 내부 정보 db-password=secret";

	private final Validator validator;

	/**
	 * 요청 바디를 검증하고 그대로 돌려준다.
	 */
	@PostMapping("/valid")
	ApiResponse<TestRequest> valid(@Valid @RequestBody TestRequest request) {
		return ApiResponse.ok(request);
	}

	/**
	 * 쿼리 파라미터 제약(@Min)을 검증한다.
	 */
	@GetMapping("/param")
	ApiResponse<Integer> param(@RequestParam @Min(1) int size) {
		return ApiResponse.ok(size);
	}

	/**
	 * 비즈니스 예외를 던진다.
	 */
	@GetMapping("/business")
	ApiResponse<Void> business() {
		throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
	}

	/**
	 * 빈 이름의 요청을 직접 검증해 ConstraintViolationException을 던진다.
	 */
	@GetMapping("/constraint")
	ApiResponse<Void> constraint() {
		Set<ConstraintViolation<TestRequest>> violations = validator.validate(new TestRequest("", 1L));
		throw new ConstraintViolationException(violations);
	}

	/**
	 * DB 무결성 위반 예외를 던진다.
	 */
	@GetMapping("/integrity")
	ApiResponse<Void> integrity() {
		throw new DataIntegrityViolationException("Duplicate entry");
	}

	/**
	 * 낙관적 락 충돌 예외를 던진다.
	 */
	@GetMapping("/lock")
	ApiResponse<Void> lock() {
		throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
	}

	/**
	 * 민감한 메시지를 담은 예상치 못한 예외를 던진다.
	 */
	@GetMapping("/error")
	ApiResponse<Void> error() {
		throw new IllegalStateException(SENSITIVE_MESSAGE);
	}

	/**
	 * 데이터를 담은 성공 응답을 돌려준다.
	 */
	@GetMapping("/ok")
	ApiResponse<TestRequest> ok() {
		return ApiResponse.ok(new TestRequest("모여", 1000L));
	}

	/**
	 * 검증 대상 테스트 요청.
	 */
	record TestRequest(@NotBlank String name, @Positive long amount) {
	}
}
