package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.MeetingGuest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모임 게스트 추가 요청(PRD G-6).
 *
 * @param name 게스트 이름(앞뒤 공백은 저장할 때 뺀다)
 */
public record MeetingGuestCreateRequest(
		@NotBlank(message = "게스트 이름을 입력해 주세요.")
		@Size(max = MeetingGuest.NAME_MAX_LENGTH, message = "게스트 이름은 20자 이하로 입력해 주세요.")
		String name) {
}
