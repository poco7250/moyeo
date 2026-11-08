package com.poco7250.moyeo.og;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.meeting.service.MeetingService;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.service.RoundPlaceService;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRD P-5 수락 기준을 실제 DB·시큐리티·템플릿으로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OgShareIntegrationTest {

	private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MeetingService meetingService;

	@Autowired
	RoundPlaceService roundPlaceService;

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
	AttendanceRepository attendanceRepository;

	@Autowired
	EntityManager entityManager;

	private Member host;
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		host = memberRepository.save(Member.ofKakao(7001L, "호스트", null, null));
		Member other = memberRepository.save(Member.ofKakao(7002L, "멤버", null, null));
		Group group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));
		groupMemberRepository.save(GroupMember.join(group, other, "멤버", GroupRole.MEMBER));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", SATURDAY, null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "밥"));
		attendanceRepository.save(Attendance.respond(round, host, AttendanceStatus.ATTEND, SATURDAY.minusDays(1)));
	}

	@Test
	void 확정된_모임_링크를_로그인_없이_열면_확정_장소와_참석_수가_OG_설명에_나온다() throws Exception {
		confirmPlace(new PlaceInput("강남 식당", null, null));

		mockMvc.perform(get("/share/meetings/" + meeting.getShareToken()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("토요일 모임 · 강남 식당 · 1/2명 참석")))
				.andExpect(content().string(containsString("property=\"og:title\" content=\"연말 모임\"")));
	}

	@Test
	void 순번_id로는_공유_페이지를_열_수_없다() throws Exception {
		mockMvc.perform(get("/share/meetings/" + meeting.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void 모임_상세는_지도_링크가_있으면_그_링크를_준다() {
		confirmPlace(new PlaceInput("강남 식당", "강남대로 1", "https://map.example.com/1"));

		MeetingDetailResponse detail = meetingService.getDetail(meeting.getId(), host.getId());

		assertThat(detail.rounds().getFirst().confirmedPlace().mapUrl()).isEqualTo("https://map.example.com/1");
		assertThat(detail.shareText()).contains("강남 식당", detail.url());
	}

	@Test
	void 모임_상세는_지도_링크가_없으면_주소로_카카오맵_검색_링크를_준다() {
		confirmPlace(new PlaceInput("Bowling", "Gangnam 1", null));

		MeetingDetailResponse detail = meetingService.getDetail(meeting.getId(), host.getId());

		assertThat(detail.rounds().getFirst().confirmedPlace().mapUrl())
				.isEqualTo("https://map.kakao.com/link/search/Gangnam%201");
	}

	private void confirmPlace(PlaceInput input) {
		roundPlaceService.confirmDirectly(round.getId(), host.getId(), input);
		entityManager.flush();
		entityManager.clear();
	}
}
