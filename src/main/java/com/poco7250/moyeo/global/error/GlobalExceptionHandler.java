package com.poco7250.moyeo.global.error;

import com.poco7250.moyeo.global.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러 밖으로 나온 예외를 공통 JSON 에러 응답(ApiResponse)으로 바꾼다.
 * 예상하지 못한 예외는 500으로 응답하고, 예외 메시지·스택트레이스는 로그에만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 비즈니스 예외를 에러 코드의 상태와 메시지로 응답한다.
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		log.warn("비즈니스 예외: code={}", e.getErrorCode());
		return toResponse(e.getErrorCode());
	}

	/**
	 * @Valid 요청 바디 검증 실패를 필드별 에러 목록과 함께 400으로 응답한다.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
		List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
	}

	/**
	 * 컨트롤러 메서드 파라미터 제약(@RequestParam @Min 등) 위반을 400으로 응답한다.
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
		List<ErrorResponse.FieldError> fieldErrors = e.getParameterValidationResults().stream()
				.flatMap(result -> toFieldErrors(result).stream())
				.toList();
		return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
	}

	/**
	 * @Validated 빈의 메서드 검증 실패를 400으로 응답한다.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
		List<ErrorResponse.FieldError> fieldErrors = e.getConstraintViolations().stream()
				.map(violation -> new ErrorResponse.FieldError(lastNodeName(violation), violation.getMessage()))
				.toList();
		return toResponse(ErrorCode.INVALID_INPUT, fieldErrors);
	}

	/**
	 * 읽을 수 없는 요청 바디(JSON 문법 오류, 정수 자리에 문자열 등)를 400으로 응답한다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
		log.warn("요청 바디 해석 실패: {}", e.getMostSpecificCause().getClass().getSimpleName());
		return toResponse(ErrorCode.INVALID_INPUT);
	}

	/**
	 * 필수 쿼리 파라미터 누락이나 경로·파라미터 타입 불일치를 400으로 응답한다.
	 */
	@ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiResponse<Void>> handleBadRequestParameter(Exception e) {
		return toResponse(ErrorCode.INVALID_INPUT);
	}

	/**
	 * 매핑되지 않은 URL 요청을 404로 응답한다.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
		return toResponse(ErrorCode.RESOURCE_NOT_FOUND);
	}

	/**
	 * 지원하지 않는 HTTP 메서드 요청을 405로 응답한다.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
	}

	/**
	 * 동시 요청 등으로 DB 유니크 제약을 위반한 경우를 409로 응답한다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
		log.warn("데이터 무결성 위반: {}", e.getMostSpecificCause().getMessage());
		return toResponse(ErrorCode.DUPLICATE_RESOURCE);
	}

	/**
	 * 낙관적 락 충돌을 409로 응답한다.
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
		log.warn("낙관적 락 충돌: entity={}, id={}", e.getPersistentClassName(), e.getIdentifier());
		return toResponse(ErrorCode.CONCURRENT_MODIFICATION);
	}

	/**
	 * 처리하지 못한 나머지 예외를 500으로 응답한다. 상세 내용은 로그에만 남긴다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("처리되지 않은 예외", e);
		return toResponse(ErrorCode.INTERNAL_ERROR);
	}

	/**
	 * 파라미터 하나의 검증 결과를 필드 에러 목록으로 바꾼다. 필드 이름은 파라미터 이름을 쓴다.
	 */
	private List<ErrorResponse.FieldError> toFieldErrors(ParameterValidationResult result) {
		String parameterName = result.getMethodParameter().getParameterName();
		return result.getResolvableErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.map(message -> new ErrorResponse.FieldError(parameterName, message))
				.toList();
	}

	/**
	 * 제약 위반 경로(예: create.request.name)의 마지막 노드 이름을 꺼낸다.
	 */
	private String lastNodeName(ConstraintViolation<?> violation) {
		String name = null;
		for (Path.Node node : violation.getPropertyPath()) {
			name = node.getName();
		}
		return name;
	}

	/**
	 * 필드 에러 없이 에러 코드로 응답을 만든다.
	 */
	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
		return toResponse(errorCode, List.of());
	}

	/**
	 * 에러 코드의 HTTP 상태와 공통 실패 응답 본문으로 응답을 만든다.
	 */
	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, List<ErrorResponse.FieldError> fieldErrors) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, fieldErrors));
	}
}
