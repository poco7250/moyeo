package com.poco7250.moyeo.og.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.global.config.ShareLinkProperties;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.config.InviteLinkProperties;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.InviteLink;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.service.InviteLinkService;
import com.poco7250.moyeo.meeting.config.MeetingLinkProperties;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.og.dto.OgMeta;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OgServiceTest {

	private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 12, 19, 18, 0);
	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 10L;
	private static final Long ROUND_ID = 100L;
	private static final String BASE = "https://api.moyeo.example.com";
	private static final String SPA = "https://moyeo.example.com";

	@Mock
	MeetingRepository meetingRepository;

	@Mock
	RoundRepository roundRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	InviteLinkService inviteLinkService;

	OgService ogService;

	private Group group;
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		ogService = new OgService(meetingRepository, roundRepository, attendanceRepository, groupMemberRepository,
				inviteLinkService, new ShareLinkProperties(BASE), new MeetingLinkProperties(SPA),
				new InviteLinkProperties(Duration.ofDays(7), SPA));
		group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		meeting = Meeting.create(group, "연말 모임", SATURDAY, null, null, 7L);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		round = Round.create(meeting, 1, "밥");
		ReflectionTestUtils.setField(round, "id", ROUND_ID);
	}

	@Test
	void 모임_메타는_1차_확정_장소와_1차_참석_수_대_그룹_멤버_수를_담는다() {
		round.confirmDirectly(PlaceCandidate.propose(round, Place.of("강남 OO식당", null, null),
				Member.ofKakao(1L, "호스트", null, null)));
		givenMeeting(5, 8);

		OgMeta meta = ogService.meetingMeta(meeting.getShareToken());

		assertThat(meta.title()).isEqualTo("연말 모임");
		assertThat(meta.description()).isEqualTo("토요일 모임 · 강남 OO식당 · 5/8명 참석");
		assertThat(meta.url()).isEqualTo(BASE + "/share/meetings/" + meeting.getShareToken());
		assertThat(meta.redirectUrl()).isEqualTo(SPA + "/meetings/" + MEETING_ID);
	}

	@Test
	void 장소가_미확정이면_장소_미정으로_나온다() {
		givenMeeting(0, 3);

		assertThat(ogService.meetingMeta(meeting.getShareToken()).description())
				.isEqualTo("토요일 모임 · 장소 미정 · 0/3명 참석");
	}

	@Test
	void 없는_토큰이면_MEETING_NOT_FOUND다() {
		given(meetingRepository.findByShareToken("없음")).willReturn(Optional.empty());

		assertThatThrownBy(() -> ogService.meetingMeta("없음"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEETING_NOT_FOUND));
	}

	@Test
	void 초대_메타는_그룹명과_멤버_수를_담고_SPA_초대_화면으로_보낸다() {
		given(inviteLinkService.getUsableLink("t")).willReturn(InviteLink.issue(group, "t", SATURDAY));
		given(groupMemberRepository.countByGroupId(GROUP_ID)).willReturn(4L);

		OgMeta meta = ogService.inviteMeta("t");

		assertThat(meta.title()).isEqualTo("대학 동기");
		assertThat(meta.description()).isEqualTo("대학 동기 그룹에 초대받았어요 · 멤버 4명");
		assertThat(meta.url()).isEqualTo(BASE + "/share/invites/t");
		assertThat(meta.redirectUrl()).isEqualTo(SPA + "/invites/t");
	}

	private void givenMeeting(long attendCount, long memberCount) {
		given(meetingRepository.findByShareToken(meeting.getShareToken())).willReturn(Optional.of(meeting));
		given(roundRepository.findAllWithConfirmedPlaceByMeetingId(MEETING_ID)).willReturn(List.of(round));
		given(attendanceRepository.countByRoundIdAndStatus(ROUND_ID, AttendanceStatus.ATTEND)).willReturn(attendCount);
		given(groupMemberRepository.countByGroupId(GROUP_ID)).willReturn(memberCount);
	}
}
