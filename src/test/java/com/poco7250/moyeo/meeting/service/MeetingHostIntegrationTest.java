package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.dto.MeetingHostResponse;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 모임 호스트 위임(PRD G-4, 오픈 이슈 15) 통합 테스트. 실시간 메시지가 커밋 후에 나가는지 보려고 실제로 커밋한다.
 * 방장 철수, 총무 민수, 멤버 영희(모임 호스트)·지수, 그룹 밖 외부인.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MeetingHostIntegrationTest {

	@Autowired
	MeetingHostService meetingHostService;

	@Autowired
	MeetingService meetingService;

	@Autowired
	SettlementConfirmService settlementConfirmService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Member owner;
	private Member treasurer;
	private Member host;
	private Member jisu;
	private Member outsider;
	private Meeting meeting;

	@BeforeEach
	void setUp() {
		owner = memberRepository.save(Member.ofKakao(5811L, "철수", null, null));
		treasurer = memberRepository.save(Member.ofKakao(5812L, "민수", null, null));
		host = memberRepository.save(Member.ofKakao(5813L, "영희", null, null));
		jisu = memberRepository.save(Member.ofKakao(5814L, "지수", null, null));
		outsider = memberRepository.save(Member.ofKakao(5815L, "외부인", null, null));
		Group group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.saveAll(List.of(GroupMember.join(group, owner, "철수", GroupRole.OWNER),
				GroupMember.join(group, treasurer, "민수", GroupRole.TREASURER),
				GroupMember.join(group, host, "영희", GroupRole.MEMBER),
				GroupMember.join(group, jisu, "지수", GroupRole.MEMBER)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0),
				null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("호스트가 위임하면 새 호스트만 확정할 수 있고 이전 호스트는 403, 모임 토픽 메시지가 1건 나간다")
	void delegate() {
		MeetingHostResponse response = meetingHostService.changeHost(meeting.getId(), host.getId(), jisu.getId());

		assertThat(response).isEqualTo(new MeetingHostResponse(meeting.getId(), jisu.getId()));
		assertThat(meetingRepository.findById(meeting.getId()).orElseThrow().getHostMemberId())
				.isEqualTo(jisu.getId());
		verify(messagingTemplate).convertAndSend(eq(RealtimeTopics.meeting(meeting.getId())),
				eq(new RealtimeMessage(MeetingHostChangedEvent.TYPE, response)));
		assertThat(meetingService.getDetail(meeting.getId(), jisu.getId()).host()).isTrue();
		assertRejected(() -> settlementConfirmService.confirm(meeting.getId(), host.getId(), true),
				ErrorCode.NOT_MEETING_HOST);
		assertThat(settlementConfirmService.confirm(meeting.getId(), jisu.getId(), true).status())
				.isEqualTo(SettlementStatus.CONFIRMED);
	}

	@Test
	@DisplayName("호스트가 연락이 안 되면 방장이 대신 넘길 수 있고, 총무는 403이다")
	void ownerCanDelegateTreasurerCannot() {
		assertRejected(() -> meetingHostService.changeHost(meeting.getId(), treasurer.getId(), jisu.getId()),
				ErrorCode.NOT_MEETING_HOST);

		meetingHostService.changeHost(meeting.getId(), owner.getId(), jisu.getId());

		assertThat(meetingRepository.findById(meeting.getId()).orElseThrow().getHostMemberId())
				.isEqualTo(jisu.getId());
	}

	@Test
	@DisplayName("모임 상세는 요청자의 호스트·정산 권한을 역할대로 알려 준다")
	void detailAuthority() {
		assertThat(meetingService.getDetail(meeting.getId(), owner.getId()))
				.extracting("host", "hostAuthority", "settlementAuthority").containsExactly(false, true, true);
		assertThat(meetingService.getDetail(meeting.getId(), treasurer.getId()))
				.extracting("host", "hostAuthority", "settlementAuthority").containsExactly(false, false, true);
		assertThat(meetingService.getDetail(meeting.getId(), jisu.getId()))
				.extracting("host", "hostAuthority", "settlementAuthority").containsExactly(false, false, false);
	}

	@Test
	@DisplayName("그룹 멤버가 아닌 대상은 404, 현재 호스트에게 넘기면 바뀌지 않고 메시지도 없다")
	void invalidTargets() {
		assertRejected(() -> meetingHostService.changeHost(meeting.getId(), host.getId(), outsider.getId()),
				ErrorCode.GROUP_MEMBER_NOT_FOUND);
		assertRejected(() -> meetingHostService.changeHost(meeting.getId(), outsider.getId(), jisu.getId()),
				ErrorCode.NOT_GROUP_MEMBER);

		meetingHostService.changeHost(meeting.getId(), host.getId(), host.getId());

		assertThat(meetingRepository.findById(meeting.getId()).orElseThrow().getHostMemberId())
				.isEqualTo(host.getId());
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	private void assertRejected(ThrowingCallable call, ErrorCode errorCode) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}
}
