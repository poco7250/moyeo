package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RoundPlaceServiceIntegrationTest {

	private static final LocalDateTime DEADLINE = LocalDateTime.of(2099, 12, 19, 18, 0);

	@Autowired
	RoundPlaceService roundPlaceService;

	@Autowired
	PlaceCandidateService placeCandidateService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	PlaceVoteRepository placeVoteRepository;

	private Member host;
	private Member member;
	private Round round;

	@BeforeEach
	void setUp() {
		host = memberRepository.save(Member.ofKakao(3001L, "호스트", null, null));
		member = memberRepository.save(Member.ofKakao(3002L, "멤버", null, null));
		Group group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));
		groupMemberRepository.save(GroupMember.join(group, member, "멤버", GroupRole.MEMBER));
		Meeting meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "밥"));
	}

	@Test
	void 투표를_열면_멤버가_후보를_추가할_수_있고_투표_중엔_바로_확정할_수_없다() {
		roundPlaceService.openVoting(round.getId(), host.getId(), DEADLINE);

		CandidateResponse added = placeCandidateService.add(round.getId(), member.getId(),
				new PlaceInput("강남 볼링", "강남대로 1", null));

		assertThat(placeCandidateRepository.findAllByRoundIdOrderById(round.getId()))
				.singleElement().satisfies(c -> {
					assertThat(c.getId()).isEqualTo(added.id());
					assertThat(c.getPlace().getLabel()).isEqualTo("강남 볼링");
					assertThat(c.getProposedBy().getId()).isEqualTo(member.getId());
				});
		assertThatThrownBy(() -> roundPlaceService.confirmDirectly(round.getId(), host.getId(),
				new PlaceInput("고깃집", null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
	}

	@Test
	void 바로_확정한_차수에_재투표를_열면_후보는_남고_표는_0건이_된다() {
		RoundPlaceResponse confirmed = roundPlaceService.confirmDirectly(round.getId(), host.getId(),
				new PlaceInput("고깃집", null, null));
		assertThat(confirmed.placeStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		placeVoteRepository.save(PlaceVote.cast(
				placeCandidateRepository.getReferenceById(confirmed.confirmedCandidateId()), member));

		RoundPlaceResponse reopened = roundPlaceService.openVoting(round.getId(), host.getId(), DEADLINE);

		assertThat(reopened.placeStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(reopened.confirmedCandidateId()).isNull();
		assertThat(reopened.candidates()).extracting(CandidateResponse::id)
				.containsExactly(confirmed.confirmedCandidateId());
		assertThat(placeVoteRepository.count()).isZero();
	}
}
