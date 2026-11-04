package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.validation.HttpsUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 직접 입력한 장소(후보 추가·바로 확정 요청 본문).
 * 카카오 데이터 무저장 원칙에 따라 장소 ID 같은 카카오 값 필드는 받지 않는다.
 * 앞뒤 공백을 지우고, 비어 있는 선택 입력은 null로 바꾼다(폼의 빈칸이 검증 실패가 되지 않게).
 */
public record PlaceInput(
		@NotBlank(message = "장소 이름을 입력해 주세요.")
		@Size(max = Place.LABEL_MAX_LENGTH, message = "장소 이름은 50자 이하로 입력해 주세요.")
		String label,

		@Size(max = Place.ADDRESS_MAX_LENGTH, message = "주소는 200자 이하로 입력해 주세요.")
		String manualAddress,

		@HttpsUrl
		String manualMapUrl) {

	public PlaceInput {
		label = label == null ? null : label.strip();
		manualAddress = blankToNull(manualAddress);
		manualMapUrl = blankToNull(manualMapUrl);
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}

	/**
	 * 장소 값 타입으로 바꾼다.
	 */
	public Place toPlace() {
		return Place.of(label, manualAddress, manualMapUrl);
	}
}
