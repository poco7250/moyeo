package com.poco7250.moyeo.place.service;

import static com.poco7250.moyeo.place.service.PlaceServiceFixture.HOST_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEETING_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEMBER_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.ROUND_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.dto.CandidateVotes;
import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaceVoteTallyTest {

	private static final Long GROUP_ID = 1L;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	RoundRepository roundRepository;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	PlaceVoteRepository placeVoteRepository;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	@InjectMocks
	PlaceVoteTally tally;

	private Round round;
	private BoardMember hostBoard;
	private BoardMember memberBoard;

	@BeforeEach
	void setUp() {
		round = PlaceServiceFixture.round();
		Group group = round.getMeeting().getGroup();
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		hostBoard = new BoardMember(HOST_ID, "호스트", null);
		memberBoard = new BoardMember(MEMBER_ID, "멤버", null);
	}

	/**
	 * 후보 3곳에 호스트는 첫째·둘째, 멤버는 첫째에 투표한 상태를 만든다(셋째는 0표).
	 * 표는 멤버 → 호스트 순으로 돌려줘 응답이 표 순서가 아닌 가입 순인지 확인한다.
	 */
	private void givenVotes() {
		Group group = round.getMeeting().getGroup();
		GroupMember host = GroupMember.join(group, PlaceServiceFixture.member(HOST_ID), "호스트", GroupRole.OWNER);
		GroupMember member = GroupMember.join(group, PlaceServiceFixture.member(MEMBER_ID), "멤버", GroupRole.MEMBER);
		PlaceCandidate first = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		PlaceCandidate second = PlaceServiceFixture.candidate(round, 501L, "횟집");
		PlaceCandidate third = PlaceServiceFixture.candidate(round, 502L, "분식집");
		given(groupMemberRepository.findAllByGroupIdOrderByIdAsc(GROUP_ID)).willReturn(List.of(host, member));
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID)).willReturn(List.of(first, second, third));
		given(placeVoteRepository.findAllByRoundId(ROUND_ID)).willReturn(List.of(
				PlaceVote.cast(first, member.getMember()),
				PlaceVote.cast(first, host.getMember()),
				PlaceVote.cast(second, host.getMember())));
	}

	private List<CandidateVotes> expectedCandidates() {
		return List.of(
				new CandidateVotes(500L, 2, List.of(hostBoard, memberBoard)),
				new CandidateVotes(501L, 1, List.of(hostBoard)),
				new CandidateVotes(502L, 0, List.of()));
	}

	@Test
	@DisplayName("후보별 득표 수와 가입 순 투표자를 돌려주고 0표 후보도 포함하며 발행하지 않는다")
	void read() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);
		givenVotes();

		RoundVotesResponse response = tally.read(ROUND_ID, MEMBER_ID);

		assertThat(response).isEqualTo(new RoundVotesResponse(ROUND_ID, expectedCandidates()));
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("비멤버는 현황을 볼 수 없다(403)")
	void readNonMember() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> tally.read(ROUND_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(placeVoteRepository);
	}

	@Test
	@DisplayName("발행 이벤트에는 모임 토픽과 득표 수·투표자 목록이 담긴다")
	void publish() {
		given(roundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
		givenVotes();

		RoundVotesResponse response = tally.publish(ROUND_ID);

		assertThat(response.candidates()).isEqualTo(expectedCandidates());
		VoteChangedEvent expected = new VoteChangedEvent(MEETING_ID, response);
		verify(eventPublisher).publishEvent(expected);
		assertThat(expected.destination()).isEqualTo("/topic/meetings/" + MEETING_ID);
		assertThat(expected.type()).isEqualTo("VOTE_CHANGED");
	}

	@Test
	@DisplayName("발행할 차수가 없으면 ROUND_NOT_FOUND다")
	void publishRoundNotFound() {
		given(roundRepository.findById(ROUND_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> tally.publish(ROUND_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_NOT_FOUND);
		verifyNoInteractions(eventPublisher);
	}
}
