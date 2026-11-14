package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MeetingTest {

	private final Meeting meeting = Meeting.create(
			Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 7L);

	@Test
	@DisplayName("모임을 만든 회원은 호스트다")
	void creatorIsHost() {
		assertThat(meeting.isHost(7L)).isTrue();
	}

	@Test
	@DisplayName("다른 회원은 호스트가 아니다")
	void otherMemberIsNotHost() {
		assertThat(meeting.isHost(8L)).isFalse();
	}

	@Test
	@DisplayName("회원 id가 null이면 호스트가 아니다")
	void nullIsNotHost() {
		assertThat(meeting.isHost(null)).isFalse();
	}

	@Test
	@DisplayName("응답 마감이 없으면 언제나 열려 있다")
	void noDeadlineIsOpen() {
		assertThat(meeting.isResponseClosed(LocalDateTime.of(2030, 1, 1, 0, 0))).isFalse();
	}

	@Test
	@DisplayName("응답 마감 시각 정각까지는 열려 있고 1초 뒤부터 닫힌다")
	void deadlineBoundary() {
		LocalDateTime deadline = LocalDateTime.of(2026, 12, 19, 18, 0);
		Meeting withDeadline = Meeting.create(
				Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, deadline, 7L);

		assertThat(withDeadline.isResponseClosed(deadline)).isFalse();
		assertThat(withDeadline.isResponseClosed(deadline.plusSeconds(1))).isTrue();
	}

	@Test
	@DisplayName("날짜가 정해진 모임은 CONFIRMED로 만들어지고, 시작 시각 없이 create하면 막는다")
	void createWithStartAtIsConfirmed() {
		assertThat(meeting.getDateStatus()).isEqualTo(MeetingDateStatus.CONFIRMED);
		assertThat(meeting.isDateVoting()).isFalse();
		assertThatThrownBy(() -> Meeting.create(Group.create("대학 동기"), "연말 모임", null, null, null, 7L))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("날짜 투표 모임은 시작 시각 없이 VOTING으로 만들어진다")
	void createWithDateVoteIsVoting() {
		Meeting voting = votingMeeting(1L);

		assertThat(voting.getStartAt()).isNull();
		assertThat(voting.getDateStatus()).isEqualTo(MeetingDateStatus.VOTING);
		assertThat(voting.isDateVoting()).isTrue();
	}

	@Test
	@DisplayName("후보로 날짜를 확정하면 시작 시각이 후보 시각이 되고 CONFIRMED가 된다")
	void confirmDateSetsStartAt() {
		Meeting voting = votingMeeting(1L);
		LocalDateTime candidateAt = LocalDateTime.of(2026, 12, 21, 19, 0);

		voting.confirmDate(MeetingDateCandidate.propose(voting, candidateAt));

		assertThat(voting.getStartAt()).isEqualTo(candidateAt);
		assertThat(voting.getDateStatus()).isEqualTo(MeetingDateStatus.CONFIRMED);
	}

	@Test
	@DisplayName("날짜가 이미 정해진 모임은 투표·확정을 409 MEETING_DATE_NOT_VOTING으로 막는다")
	void confirmedRejectsDateVoting() {
		Meeting voting = votingMeeting(1L);
		voting.confirmDate(MeetingDateCandidate.propose(voting, LocalDateTime.of(2026, 12, 21, 19, 0)));

		assertThatThrownBy(voting::requireDateVoting)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETING_DATE_NOT_VOTING);
		assertThatThrownBy(() -> voting.confirmDate(MeetingDateCandidate.propose(voting,
				LocalDateTime.of(2026, 12, 22, 19, 0))))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETING_DATE_NOT_VOTING);
		assertThat(voting.getStartAt()).isEqualTo(LocalDateTime.of(2026, 12, 21, 19, 0));
	}

	@Test
	@DisplayName("다른 모임의 후보로 확정하면 호출 쪽 버그로 보고 막는다")
	void confirmDateRejectsOtherMeetingCandidate() {
		Meeting voting = votingMeeting(1L);
		Meeting other = votingMeeting(2L);

		assertThatThrownBy(() -> voting.confirmDate(MeetingDateCandidate.propose(other,
				LocalDateTime.of(2026, 12, 21, 19, 0))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(voting.isDateVoting()).isTrue();
	}

	private static Meeting votingMeeting(Long id) {
		Meeting voting = Meeting.createWithDateVote(Group.create("대학 동기"), "연말 모임", null, null, 7L);
		ReflectionTestUtils.setField(voting, "id", id);
		return voting;
	}
}
