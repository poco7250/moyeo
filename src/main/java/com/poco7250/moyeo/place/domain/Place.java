package com.poco7250.moyeo.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 값 타입. 전부 사용자가 직접 입력한 값만 담는다.
 * 카카오 데이터 무저장 원칙(PRD 오픈 이슈 5)에 따라 장소 ID·주소·좌표 등 카카오 응답 값 컬럼은 두지 않는다.
 * 지도 링크가 없으면 저장하지 않고 조회 시점에 PlaceLinkFactory로 만든다.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

	public static final int LABEL_MAX_LENGTH = 50;
	public static final int ADDRESS_MAX_LENGTH = 200;
	public static final int MAP_URL_MAX_LENGTH = 2048;

	// 표시 이름(필수)
	@Column(nullable = false, length = LABEL_MAX_LENGTH)
	private String label;

	// 주소 텍스트(선택)
	@Column(length = ADDRESS_MAX_LENGTH)
	private String manualAddress;

	// 지도 링크(선택, https만 — 요청 DTO의 @HttpsUrl로 검증)
	@Column(length = MAP_URL_MAX_LENGTH)
	private String manualMapUrl;

	private Place(String label, String manualAddress, String manualMapUrl) {
		this.label = label;
		this.manualAddress = manualAddress;
		this.manualMapUrl = manualMapUrl;
	}

	/**
	 * 사용자 입력으로 장소 값을 만든다.
	 */
	public static Place of(String label, String manualAddress, String manualMapUrl) {
		return new Place(label, manualAddress, manualMapUrl);
	}
}
