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
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
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

	@Mock
	MeetingRepository meetingRepository;

	@Mock
	GroupAccessChecker groupAccessChecker;

	@InjectMocks
	MeetingAccessChecker meetingAccessChecker;

	private Meeting meeting;

	@BeforeEach
	void setUp() {
		Group group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		meeting = Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, HOST_ID);
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
}
