package com.poco7250.moyeo.place.dto;

/**
 * 장소 검색 결과 한 건(표시용). 좌표는 지도 URL 링크 방침이라 내리지 않는다.
 *
 * @param kakaoPlaceId 카카오 장소 ID(후보 등록 시 이 값만 저장)
 * @param name         장소 이름(후보 label 기본값)
 * @param categoryName 카테고리
 * @param address      지번 주소
 * @param roadAddress  도로명 주소
 * @param phone        전화번호
 * @param placeUrl     카카오맵 장소 상세 URL
 */
public record PlaceSearchItem(
		String kakaoPlaceId,
		String name,
		String categoryName,
		String address,
		String roadAddress,
		String phone,
		String placeUrl) {

	/**
	 * 카카오 응답의 장소 한 건을 표시용 DTO로 바꾼다.
	 */
	public static PlaceSearchItem from(KakaoLocalSearchResponse.Document document) {
		return new PlaceSearchItem(document.id(), document.placeName(), document.categoryName(),
				document.addressName(), document.roadAddressName(), document.phone(), document.placeUrl());
	}
}
