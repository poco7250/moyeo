package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.config.ShareLinkProperties;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import com.poco7250.moyeo.meeting.dto.GroupMeetingsResponse;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.dto.RoundResponse;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 10L;
	private static final Long HOST_ID = 7L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
	private static final LocalDateTime START_AT = LocalDateTime.of(2026, 12, 20, 18, 0);

	@Mock
	MeetingRepository meetingRepository;

	@Mock
	RoundRepository roundRepository;

	@Mock
	SettlementRepository settlementRepository;

	@Mock
	GroupRepository groupRepository;

	@Mock
	GroupAccessChecker groupAccessChecker;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	MeetingService meetingService;

	private Group group;

	@BeforeEach
	void setUp() {
		meetingService = new MeetingService(meetingRepository, roundRepository, settlementRepository,
				groupRepository, groupAccessChecker, meetingAccessChecker, new ShareLinkProperties(""),
				TestClocks.fixed(NOW));
		group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
	}

	@Test
	void 템플릿으로_모임을_만들면_생성자가_호스트이고_차수와_DRAFT_정산이_함께_생긴다() {
		givenSaves();

		MeetingDetailResponse response = meetingService.create(GROUP_ID, HOST_ID,
				request(RoundTemplate.MEAL_BOWLING, null));

		ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
		verify(settlementRepository).save(settlement.capture());
		assertThat(settlement.getValue().getStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(settlement.getValue().getMeeting().getHostMemberId()).isEqualTo(HOST_ID);

		assertThat(response.hostMemberId()).isEqualTo(HOST_ID);
		assertThat(response.host()).isTrue();
		assertThat(response.url()).matches("/share/meetings/[A-Za-z0-9_-]{43}");
		assertThat(response.shareText()).contains("장소 미정", response.url());
		assertThat(response.rounds()).extracting(RoundResponse::seq).containsExactly(1, 2);
		assertThat(response.rounds()).extracting(RoundResponse::name).containsExactly("밥", "볼링");
		assertThat(response.rounds()).extracting(RoundResponse::placeStatus)
				.containsOnly(PlaceStatus.UNDECIDED);
	}

	@Test
	void 직접_구성하면_이름_앞뒤_공백을_지운_차수가_순서대로_생긴다() {
		givenSaves();

		MeetingDetailResponse response = meetingService.create(GROUP_ID, HOST_ID,
				request(null, List.of(" 고기 ", "노래방", "카페")));

		assertThat(response.rounds()).extracting(RoundResponse::name).containsExactly("고기", "노래방", "카페");
		assertThat(response.rounds()).extracting(RoundResponse::seq).containsExactly(1, 2, 3);
	}

	@Test
	void 비멤버면_NOT_GROUP_MEMBER이고_아무것도_저장하지_않는다() {
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
				.given(groupAccessChecker).requireMember(GROUP_ID, 8L);

		assertThatThrownBy(() -> meetingService.create(GROUP_ID, 8L, request(RoundTemplate.MEAL_ONLY, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(meetingRepository, roundRepository, settlementRepository);
	}

	@Test
	void 상세_조회는_요청자가_호스트인지_알려준다() {
		Meeting meeting = meeting(START_AT);
		given(meetingAccessChecker.requireMember(MEETING_ID, 8L)).willReturn(meeting);
		given(roundRepository.findAllWithConfirmedPlaceByMeetingId(MEETING_ID))
				.willReturn(List.of(Round.create(meeting, 1, "밥")));

		MeetingDetailResponse response = meetingService.getDetail(MEETING_ID, 8L);

		assertThat(response.host()).isFalse();
		assertThat(response.groupId()).isEqualTo(GROUP_ID);
		assertThat(response.rounds()).hasSize(1);
	}

	@Test
	void 그룹_모임_목록은_현재_시각_기준으로_다가오는_모임과_지난_모임을_나눠_조회한다() {
		given(meetingRepository.findAllByGroupIdAndStartAtGreaterThanEqualOrderByStartAtAsc(GROUP_ID, NOW))
				.willReturn(List.of(meeting(NOW)));
		given(meetingRepository.findAllByGroupIdAndStartAtLessThanOrderByStartAtDesc(GROUP_ID, NOW))
				.willReturn(List.of(meeting(NOW.minusDays(1))));

		GroupMeetingsResponse response = meetingService.getGroupMeetings(GROUP_ID, HOST_ID);

		verify(groupAccessChecker).requireMember(GROUP_ID, HOST_ID);
		assertThat(response.upcoming()).singleElement().extracting("startAt").isEqualTo(NOW);
		assertThat(response.past()).singleElement().extracting("startAt").isEqualTo(NOW.minusDays(1));
	}

	private void givenSaves() {
		given(groupRepository.getReferenceById(GROUP_ID)).willReturn(group);
		given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
			Meeting saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", MEETING_ID);
			return saved;
		});
		given(roundRepository.saveAll(anyList())).willAnswer(AdditionalAnswers.returnsFirstArg());
	}

	private Meeting meeting(LocalDateTime startAt) {
		Meeting meeting = Meeting.create(group, "연말 모임", startAt, null, null, HOST_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		return meeting;
	}

	private MeetingCreateRequest request(RoundTemplate template, List<String> rounds) {
		return new MeetingCreateRequest("연말 모임", START_AT, "메모", null, template, rounds);
	}
}
