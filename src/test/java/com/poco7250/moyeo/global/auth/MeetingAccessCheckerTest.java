package com.poco7250.moyeo.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingAccessCheckerTest {

	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 10L;
	private static final Long HOST_ID = 7L;
	private static final Long MEMBER_ID = 8L;
	private static final Long ROUND_ID = 100L;

	@Mock
	MeetingRepository meetingRepository;

	@Mock
	GroupAccessChecker groupAccessChecker;

	@Mock
	RoundRepository roundRepository;

	@InjectMocks
	MeetingAccessChecker meetingAccessChecker;

	private Meeting meeting;

	@BeforeEach
	void setUp() {
		Group group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		meeting = Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, HOST_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
	}

	@Test
	void 그룹_멤버면_모임을_돌려준다() {
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.of(meeting));

		assertThat(meetingAccessChecker.requireMember(MEETING_ID, MEMBER_ID)).isSameAs(meeting);
	}

	@Test
	void 없는_모임이면_존재를_숨기고_NOT_GROUP_MEMBER() {
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> meetingAccessChecker.requireMember(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(groupAccessChecker);
	}

	@Test
	void 비멤버면_NOT_GROUP_MEMBER() {
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.of(meeting));
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
				.given(groupAccessChecker).requireMember(GROUP_ID, MEMBER_ID);

		assertThatThrownBy(() -> meetingAccessChecker.requireHost(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	@Test
	void 호스트가_아니면_NOT_MEETING_HOST() {
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.of(meeting));

		assertThatThrownBy(() -> meetingAccessChecker.requireHost(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
	}

	@Test
	void 호스트면_모임을_돌려준다() {
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.of(meeting));

		assertThat(meetingAccessChecker.requireHost(MEETING_ID, HOST_ID)).isSameAs(meeting);
	}

	@Test
	void 없는_차수면_존재를_숨기고_NOT_GROUP_MEMBER() {
		given(roundRepository.findById(ROUND_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(meetingRepository, groupAccessChecker);
	}

	@Test
	void 차수가_속한_그룹_멤버면_차수를_돌려준다() {
		Round round = Round.create(meeting, 1, "밥");
		given(roundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.of(meeting));

		assertThat(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).isSameAs(round);
	}

	@Test
	void 차수_호스트_검사는_호스트가_아니면_NOT_MEETING_HOST_호스트면_차수를_돌려준다() {
		Round round = Round.create(meeting, 1, "밥");
		given(roundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
		given(meetingRepository.findById(MEETING_ID)).willReturn(Optional.of(meeting));

		assertThatThrownBy(() -> meetingAccessChecker.requireRoundHost(ROUND_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		assertThat(meetingAccessChecker.requireRoundHost(ROUND_ID, HOST_ID)).isSameAs(round);
	}
}
