package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;

/**
 * 429 ATTENDANCE_REMINDER_COOLDOWN 응답의 error.details.
 *
 * @param nextAvailableAt 다시 보낼 수 있는 시각
 */
public record ReminderCooldownDetails(LocalDateTime nextAvailableAt) {
}
