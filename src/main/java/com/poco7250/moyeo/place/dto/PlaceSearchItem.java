package com.poco7250.moyeo.place.dto;

/**
 * 장소 검색 결과 한 건(참고용 표시 전용). 어떤 값도 저장하거나 후보 입력값으로 넘기지 않는다(PRD 카카오 데이터 무저장).
 * 좌표는 지도 URL 링크 방침이라 내리지 않는다.
 *
 * @param kakaoPlaceId 카카오 장소 ID(화면 목록 키 용도, 저장 안 함)
 * @param name         장소 이름(사용자가 보고 직접 입력할 참고 정보, 입력칸 자동 채우기 금지)
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
