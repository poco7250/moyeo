package com.poco7250.moyeo.meeting.domain;

import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ABSENT;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ATTEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceShortcutTest {

	@Test
	@DisplayName("차수 2개: 늦참은 (불참, 참석), 1차만은 (참석, 불참)")
	void twoRounds() {
		assertThat(AttendanceShortcut.LATE_FROM_SECOND.expand(2)).containsExactly(ABSENT, ATTEND);
		assertThat(AttendanceShortcut.FIRST_ONLY.expand(2)).containsExactly(ATTEND, ABSENT);
	}

	@Test
	@DisplayName("차수 3개: 1차 기준으로 나머지는 모두 같은 상태다")
	void threeRounds() {
		assertThat(AttendanceShortcut.LATE_FROM_SECOND.expand(3)).containsExactly(ABSENT, ATTEND, ATTEND);
		assertThat(AttendanceShortcut.FIRST_ONLY.expand(3)).containsExactly(ATTEND, ABSENT, ABSENT);
	}

	@Test
	@DisplayName("차수가 1개면 단축 옵션을 쓸 수 없다")
	void singleRound() {
		assertThatThrownBy(() -> AttendanceShortcut.FIRST_ONLY.expand(1))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_SHORTCUT_NOT_APPLICABLE);
	}
}
