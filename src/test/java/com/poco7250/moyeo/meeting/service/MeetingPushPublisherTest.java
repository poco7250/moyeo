package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.config.MeetingLinkProperties;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.service.PushNotificationEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MeetingPushPublisher 단위 테스트. 받는 사람·문구·태그를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class MeetingPushPublisherTest {

	private static final LocalDateTime START_AT = LocalDateTime.of(2099, 12, 19, 18, 0);

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	MeetingPushPublisher publisher;
	Meeting meeting;

	@BeforeEach
	void setUp() {
		publisher = new MeetingPushPublisher(groupMemberRepository, attendanceRepository,
				new MeetingLinkProperties("https://moyeo.example.com"), eventPublisher);
		Group group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", 1L);
		meeting = Meeting.create(group, "연말 모임", START_AT, null, START_AT.minusDays(1), 7L);
		ReflectionTestUtils.setField(meeting, "id", 10L);
	}

	@Test
	@DisplayName("새 모임은 만든 사람을 뺀 그룹 멤버 전원에게, 모임 링크·태그와 함께 보낸다")
	void meetingCreated() {
		given(groupMemberRepository.findMemberIdsByGroupId(1L)).willReturn(List.of(7L, 8L, 9L));

		publisher.meetingCreated(meeting, 7L);

		PushNotificationEvent event = captured();
		assertThat(event.type()).isEqualTo(PushType.MEETING_CREATED);
		assertThat(event.recipientIds()).containsExactlyInAnyOrder(8L, 9L);
		assertThat(event.title()).isEqualTo("새 모임: 연말 모임");
		assertThat(event.body()).isEqualTo("12월 19일(토) 18:00 · 참석 응답 부탁해!");
		assertThat(event.url()).isEqualTo("https://moyeo.example.com/meetings/10");
		assertThat(event.tag()).isEqualTo("meeting-10-meeting_created");
	}

	@Test
	@DisplayName("장소 확정은 확정한 사람과 그 차수 불참자를 빼고, 태그에 차수 id를 붙인다")
	void placeConfirmed() {
		Round round = Round.create(meeting, 1, "1차");
		ReflectionTestUtils.setField(round, "id", 100L);
		round.confirmDirectly(PlaceCandidate.propose(round, Place.of("강남 OO식당", null, null),
				Member.ofKakao(7L, "호스트", null, null)));
		given(groupMemberRepository.findMemberIdsByGroupId(1L)).willReturn(List.of(7L, 8L, 9L, 10L));
		given(attendanceRepository.findAbsentMemberIdsByRoundId(100L)).willReturn(List.of(9L));

		publisher.placeConfirmed(round, 7L);

		PushNotificationEvent event = captured();
		assertThat(event.recipientIds()).containsExactlyInAnyOrder(8L, 10L);
		assertThat(event.title()).isEqualTo("연말 모임 장소 확정");
		assertThat(event.body()).isEqualTo("1차 장소: 강남 OO식당");
		assertThat(event.tag()).isEqualTo("meeting-10-place_confirmed-100");
	}

	@Test
	@DisplayName("자동 마감 확정(확정한 사람 없음)은 불참자만 뺀다")
	void placeConfirmedByCloser() {
		Round round = Round.create(meeting, 1, "1차");
		ReflectionTestUtils.setField(round, "id", 100L);
		round.confirmDirectly(PlaceCandidate.propose(round, Place.of("볼링장", null, null),
				Member.ofKakao(7L, "호스트", null, null)));
		given(groupMemberRepository.findMemberIdsByGroupId(1L)).willReturn(List.of(7L, 8L));
		given(attendanceRepository.findAbsentMemberIdsByRoundId(100L)).willReturn(List.of());

		publisher.placeConfirmed(round, null);

		assertThat(captured().recipientIds()).containsExactlyInAnyOrder(7L, 8L);
	}

	@Test
	@DisplayName("리마인드는 미응답자에게 마감 시각을 넣어 보내고, 받을 사람이 없으면 발행하지 않는다")
	void reminderAndEmpty() {
		publisher.reminder(meeting, List.of(8L));

		PushNotificationEvent event = captured();
		assertThat(event.type()).isEqualTo(PushType.ATTENDANCE_REMINDER);
		assertThat(event.body()).isEqualTo("아직 참석 응답을 안 했어. 12월 18일(금) 18:00까지 응답 부탁해!");

		given(groupMemberRepository.findMemberIdsByGroupId(1L)).willReturn(List.of(7L));
		publisher.dateConfirmed(meeting, 7L);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	private PushNotificationEvent captured() {
		ArgumentCaptor<PushNotificationEvent> captor = ArgumentCaptor.forClass(PushNotificationEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		clearInvocations(eventPublisher);
		return captor.getValue();
	}
}
