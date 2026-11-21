package com.poco7250.moyeo.push.dto;

import com.poco7250.moyeo.push.domain.PushType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 푸시 종류별 받기 설정 변경. 보낸 종류만 바꾸고 나머지는 그대로 둔다.
 *
 * @param enabled 종류 → 받기 여부(예: {"MEETING_CREATED": false})
 */
public record PushPreferencesRequest(@NotEmpty Map<PushType, @NotNull Boolean> enabled) {
}
