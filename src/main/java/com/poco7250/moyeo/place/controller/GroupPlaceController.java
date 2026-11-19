package com.poco7250.moyeo.place.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.place.dto.FavoritePlaceResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RecentPlaceResponse;
import com.poco7250.moyeo.place.service.GroupPlaceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 단골 장소·지난 장소 API(PRD P-8). 전부 로그인이 필요하고 그룹 멤버만 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class GroupPlaceController {

	private final GroupPlaceService groupPlaceService;

	/**
	 * 그룹 단골 장소를 최근에 저장한 순으로 조회한다.
	 */
	@GetMapping("/favorite-places")
	public ApiResponse<List<FavoritePlaceResponse>> getFavorites(@LoginMember Long memberId,
			@PathVariable Long groupId) {
		return ApiResponse.ok(groupPlaceService.listFavorites(groupId, memberId));
	}

	/**
	 * 직접 입력한 장소를 그룹 단골로 저장한다.
	 */
	@PostMapping("/favorite-places")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FavoritePlaceResponse> addFavorite(@LoginMember Long memberId, @PathVariable Long groupId,
			@Valid @RequestBody PlaceInput request) {
		return ApiResponse.ok(groupPlaceService.addFavorite(groupId, memberId, request));
	}

	/**
	 * 그룹 단골 장소를 지운다.
	 */
	@DeleteMapping("/favorite-places/{favoriteId}")
	public ApiResponse<Void> deleteFavorite(@LoginMember Long memberId, @PathVariable Long groupId,
			@PathVariable Long favoriteId) {
		groupPlaceService.deleteFavorite(groupId, favoriteId, memberId);
		return ApiResponse.ok();
	}

	/**
	 * 지난 모임에서 확정된 장소를 같은 장소끼리 합쳐 최근 순으로 조회한다.
	 */
	@GetMapping("/recent-places")
	public ApiResponse<List<RecentPlaceResponse>> getRecentPlaces(@LoginMember Long memberId,
			@PathVariable Long groupId) {
		return ApiResponse.ok(groupPlaceService.listRecentPlaces(groupId, memberId));
	}
}
