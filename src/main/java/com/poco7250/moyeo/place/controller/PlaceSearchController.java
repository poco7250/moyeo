package com.poco7250.moyeo.place.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.place.dto.PlaceSearchResponse;
import com.poco7250.moyeo.place.service.PlaceSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 검색 API. 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceSearchController {

	// 카카오 키워드 검색이 허용하는 최대 페이지
	private static final int MAX_PAGE = 45;

	private final PlaceSearchService placeSearchService;

	/**
	 * 카카오 로컬 키워드 검색 결과를 돌려준다. 카카오 장애·쿼터 초과면 503 PLACE_SEARCH_UNAVAILABLE(직접 입력 폴백 안내).
	 */
	@GetMapping("/search")
	public ApiResponse<PlaceSearchResponse> search(@LoginMember Long memberId,
			@RequestParam @NotBlank @Size(max = 100) String query,
			@RequestParam(defaultValue = "1") @Min(1) @Max(MAX_PAGE) int page) {
		return ApiResponse.ok(placeSearchService.search(memberId, query, page));
	}
}
