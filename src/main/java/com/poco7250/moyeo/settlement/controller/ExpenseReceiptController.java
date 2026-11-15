package com.poco7250.moyeo.settlement.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptResponse;
import com.poco7250.moyeo.settlement.service.ExpenseReceiptService;
import com.poco7250.moyeo.settlement.service.ReceiptFile;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 지출 영수증 사진 API(PRD S-1 확장). 전부 로그인이 필요하고, 모임이 속한 그룹의 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/expenses/{expenseId}/receipt")
@RequiredArgsConstructor
public class ExpenseReceiptController {

	// 응답의 url에 버전(v)이 붙어 교체되면 주소가 바뀌므로 길게 캐시해도 된다. 로그인 사용자 전용이라 private
	private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

	private final ExpenseReceiptService expenseReceiptService;

	/**
	 * 영수증 사진을 올린다(multipart 파트 이름 file). 이미 있으면 교체한다.
	 */
	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<ExpenseReceiptResponse> upload(@LoginMember Long memberId, @PathVariable Long expenseId,
			@RequestPart("file") MultipartFile file) {
		return ApiResponse.ok(expenseReceiptService.upload(expenseId, memberId, file));
	}

	/**
	 * 영수증 사진을 지운다.
	 */
	@DeleteMapping
	public ApiResponse<Void> delete(@LoginMember Long memberId, @PathVariable Long expenseId) {
		expenseReceiptService.delete(expenseId, memberId);
		return ApiResponse.ok();
	}

	/**
	 * 영수증 사진 파일을 내려준다. 응답 본문은 공통 래퍼가 아닌 이미지 바이트다(에러는 공통 JSON).
	 */
	@GetMapping
	public ResponseEntity<Resource> download(@LoginMember Long memberId, @PathVariable Long expenseId) {
		ReceiptFile file = expenseReceiptService.load(expenseId, memberId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.size())
				.cacheControl(CACHE_CONTROL)
				.eTag(file.etag())
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(file.resource());
	}
}
