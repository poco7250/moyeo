package com.poco7250.moyeo.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.push.PushSendResult;
import com.poco7250.moyeo.global.push.PushTarget;
import com.poco7250.moyeo.global.push.WebPushClient;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingDateCandidate;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingDateCandidateRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.meeting.service.AttendanceReminderPushService;
import com.poco7250.moyeo.meeting.service.MeetingDateService;
import com.poco7250.moyeo.meeting.service.MeetingService;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.service.RoundPlaceService;
import com.poco7250.moyeo.push.domain.PushSubscription;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.dto.PushKeysRequest;
import com.poco7250.moyeo.push.dto.PushSubscribeRequest;
import com.poco7250.moyeo.push.repository.PushSubscriptionRepository;
import com.poco7250.moyeo.support.ConcurrentRunner;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 웹 푸시(Task 056) 통합 테스트. 커밋 뒤 비동기로 나가는지 보려고 실제로 커밋하고 끝나면 지운다.
 * 푸시 서비스 호출(WebPushClient)만 목으로 바꿔 어떤 기기로 나갔는지 기록한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WebPushIntegrationTest {

	private static final long WAIT_MS = 5_000;
	private static final LocalDateTime START_AT = LocalDateTime.of(2099, 12, 19, 18, 0);
	// RFC 8291 부록 A의 기기 키(구독 검증만 통과하면 되고, 전송은 목이라 실제로 암호화하지 않는다)
	private static final PushKeysRequest KEYS = new PushKeysRequest(
			"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
			"BTBZMqHH6r4Tts7J_aSIgg");

	@MockitoBean
	WebPushClient webPushClient;

	@Autowired
	PushSubscriptionService pushSubscriptionService;

	@Autowired
	PushSubscriptionRepository pushSubscriptionRepository;

	@Autowired
	AttendanceReminderPushService attendanceReminderPushService;

	@Autowired
	MeetingService meetingService;

	@Autowired
	MeetingDateService meetingDateService;

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
	MeetingDateCandidateRepository meetingDateCandidateRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = join(6101L, "호스트", GroupRole.OWNER);
		minsu = join(6102L, "민수", GroupRole.MEMBER);
		younghee = join(6103L, "영희", GroupRole.MEMBER);
		given(webPushClient.isEnabled()).willReturn(true);
		given(webPushClient.send(any(), any(), any())).willReturn(PushSendResult.SENT);
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("리마인드 푸시는 미응답자 기기에만 나가고 끈 회원은 빠지며, 410 기기는 지워지고 1시간 안 재요청은 429·전송 없음")
	void reminderPush() {
		Meeting meeting = meetingWithRound();
		subscribe(host, "host");
		subscribe(minsu, "minsu-phone");
		subscribe(minsu, "minsu-laptop");
		subscribe(younghee, "younghee");
		pushSubscriptionService.updatePreferences(younghee.getId(), Map.of(PushType.ATTENDANCE_REMINDER, false));
		given(webPushClient.send(eq(target("minsu-laptop")), any(), any())).willReturn(PushSendResult.GONE);

		attendanceReminderPushService.push(meeting.getId(), host.getId());

		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("minsu-phone")), any(), eq(Duration.ofHours(6)));
		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("minsu-laptop")), any(), any());
		verify(webPushClient, after(500).never()).send(eq(target("younghee")), any(), any());
		verify(webPushClient, never()).send(eq(target("host")), any(), any());
		assertThat(endpointsOf(minsu)).containsExactly(endpoint("minsu-phone"));

		clearInvocations(webPushClient);
		assertThatThrownBy(() -> attendanceReminderPushService.push(meeting.getId(), minsu.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_REMINDER_COOLDOWN);
		verify(webPushClient, after(500).never()).send(any(), any(), any());
	}

	@Test
	@DisplayName("같은 모임 리마인드 푸시 2건이 동시에 오면 1건만 성공하고 나머지는 429다")
	void concurrentReminder() {
		Meeting meeting = meetingWithRound();
		subscribe(minsu, "minsu-phone");

		List<ConcurrentRunner.Outcome<Object>> outcomes = ConcurrentRunner.runTogether(List.of(
				() -> attendanceReminderPushService.push(meeting.getId(), host.getId()),
				() -> attendanceReminderPushService.push(meeting.getId(), minsu.getId())));

		assertThat(outcomes).filteredOn(ConcurrentRunner.Outcome::succeeded).hasSize(1);
		assertThat(outcomes).filteredOn(o -> !o.succeeded()).singleElement()
				.extracting(o -> ((BusinessException) o.failure()).getErrorCode())
				.isEqualTo(ErrorCode.ATTENDANCE_REMINDER_COOLDOWN);
		verify(webPushClient, timeout(WAIT_MS).times(1)).send(eq(target("minsu-phone")), any(), any());
	}

	@Test
	@DisplayName("새 모임 푸시는 만든 사람을 뺀 멤버에게 제목·링크가 담긴 JSON으로 나간다")
	void meetingCreated() {
		subscribe(host, "host");
		subscribe(minsu, "minsu-phone");
		subscribe(younghee, "younghee");

		Long meetingId = meetingService.create(group.getId(), host.getId(), new MeetingCreateRequest("연말 모임",
				START_AT, null, null, null, List.of("1차"), null)).id();

		ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("minsu-phone")), payload.capture(),
				eq(Duration.ofHours(24)));
		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("younghee")), any(), any());
		verify(webPushClient, after(500).never()).send(eq(target("host")), any(), any());
		assertThat(new String(payload.getValue(), StandardCharsets.UTF_8))
				.contains("\"type\":\"MEETING_CREATED\"", "\"title\":\"새 모임: 연말 모임\"",
						"\"url\":\"/meetings/" + meetingId + "\"");
	}

	@Test
	@DisplayName("날짜 확정은 호스트를 뺀 멤버에게, 장소 확정은 호스트와 그 차수 불참자를 뺀 멤버에게 나간다")
	void dateAndPlaceConfirmed() {
		Meeting meeting = meetingRepository.save(Meeting.createWithDateVote(group, "연말 모임", null, null,
				host.getId()));
		Round round = roundRepository.save(Round.create(meeting, 1, "1차"));
		MeetingDateCandidate candidate = meetingDateCandidateRepository.save(MeetingDateCandidate.propose(meeting,
				START_AT));
		attendanceRepository.save(Attendance.respond(round, younghee, AttendanceStatus.ABSENT, LocalDateTime.now()));
		subscribe(host, "host");
		subscribe(minsu, "minsu-phone");
		subscribe(younghee, "younghee");

		meetingDateService.confirm(meeting.getId(), host.getId(), candidate.getId());

		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("minsu-phone")), any(), any());
		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("younghee")), any(), any());
		verify(webPushClient, after(500).never()).send(eq(target("host")), any(), any());

		clearInvocations(webPushClient);
		roundPlaceService.confirmDirectly(round.getId(), host.getId(), new PlaceInput("강남 OO식당", null, null));

		verify(webPushClient, timeout(WAIT_MS)).send(eq(target("minsu-phone")), any(), any());
		verify(webPushClient, after(500).never()).send(eq(target("younghee")), any(), any());
		verify(webPushClient, never()).send(eq(target("host")), any(), any());
	}

	@Test
	@DisplayName("트랜잭션이 실패하면(비호스트 장소 확정) 푸시가 나가지 않는다")
	void noPushOnRollback() {
		Meeting meeting = meetingWithRound();
		Round round = roundRepository.findAllByMeetingIdOrderBySeq(meeting.getId()).getFirst();
		subscribe(younghee, "younghee");

		assertThatThrownBy(() -> roundPlaceService.confirmDirectly(round.getId(), minsu.getId(),
				new PlaceInput("볼링장", null, null))).isInstanceOf(BusinessException.class);

		verify(webPushClient, after(500).never()).send(any(), any(), any());
	}

	private Meeting meetingWithRound() {
		Meeting meeting = meetingRepository.save(Meeting.create(group, "연말 모임", START_AT, null,
				START_AT.minusDays(1), host.getId()));
		Round round = roundRepository.save(Round.create(meeting, 1, "1차"));
		attendanceRepository.save(Attendance.respond(round, host, AttendanceStatus.ATTEND, LocalDateTime.now()));
		return meeting;
	}

	private Member join(Long kakaoId, String nickname, GroupRole role) {
		Member member = memberRepository.save(Member.ofKakao(kakaoId, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, role));
		return member;
	}

	private void subscribe(Member member, String device) {
		pushSubscriptionService.subscribe(member.getId(), new PushSubscribeRequest(endpoint(device), KEYS));
	}

	private List<String> endpointsOf(Member member) {
		return pushSubscriptionRepository.findAllByMemberIdOrderByRegisteredAtDescIdDesc(member.getId()).stream()
				.map(PushSubscription::getEndpoint).toList();
	}

	private static PushTarget target(String device) {
		return new PushTarget(endpoint(device), KEYS.p256dh(), KEYS.auth());
	}

	private static String endpoint(String device) {
		return "https://fcm.googleapis.com/fcm/send/" + device;
	}
}
