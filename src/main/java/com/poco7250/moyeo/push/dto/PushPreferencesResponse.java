package com.poco7250.moyeo.push.dto;

import com.poco7250.moyeo.push.domain.PushType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * 푸시 종류별 받기 설정. 모든 종류가 정의 순서대로 들어 있다.
 *
 * @param enabled 종류 → 받기 여부
 */
public record PushPreferencesResponse(Map<PushType, Boolean> enabled) {

	/**
	 * 꺼 둔 종류 목록으로 전체 설정을 만든다(나머지는 켬).
	 */
	public static PushPreferencesResponse fromMuted(Collection<PushType> muted) {
		Map<PushType, Boolean> enabled = new EnumMap<>(PushType.class);
		for (PushType type : PushType.values()) {
			enabled.put(type, !muted.contains(type));
		}
		return new PushPreferencesResponse(enabled);
	}
}
