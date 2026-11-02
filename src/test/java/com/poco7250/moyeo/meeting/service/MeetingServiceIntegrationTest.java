package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MeetingServiceIntegrationTest {

	@Autowired
	MeetingService meetingService;

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
	SettlementRepository settlementRepository;

	@Test
	void 밥_볼링_템플릿으로_모임을_만들면_모임_차수_2개_DRAFT_정산이_DB에_저장된다() {
		Member host = memberRepository.save(Member.ofKakao(1001L, "호스트", null, null));
		Group group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));

		MeetingDetailResponse response = meetingService.create(group.getId(), host.getId(),
				new MeetingCreateRequest("연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), "메모", null,
						RoundTemplate.MEAL_BOWLING, null));

		assertThat(meetingRepository.findById(response.id()))
				.hasValueSatisfying(meeting -> assertThat(meeting.getHostMemberId()).isEqualTo(host.getId()));
		List<Round> rounds = roundRepository.findAllByMeetingIdOrderBySeq(response.id());
		assertThat(rounds).extracting(Round::getName).containsExactly("밥", "볼링");
		assertThat(rounds).extracting(Round::getPlaceStatus).containsOnly(PlaceStatus.UNDECIDED);
		assertThat(settlementRepository.findAll())
				.filteredOn(settlement -> settlement.getMeeting().getId().equals(response.id()))
				.singleElement().extracting(Settlement::getStatus).isEqualTo(SettlementStatus.DRAFT);
	}
}
