package com.poco7250.moyeo.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.global.websocket.WebSocketConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.meeting.service.AttendanceChangedEvent;
import com.poco7250.moyeo.meeting.service.AttendanceService;
import com.poco7250.moyeo.meeting.service.AttendanceWriter;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.MeetingPenalties;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementInputAssembler;
import com.poco7250.moyeo.settlement.service.SettlementReconfirmedEvent;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 서버에 STOMP 구독자로 붙어, 실제 서비스 쓰기가 커밋되면 메시지가 정확히 1건, 롤백되면 0건 오는지 본다(Task 042).
 * 서버 트랜잭션이 실제로 커밋되므로 끝나면 전체 테이블을 비운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RealtimeCommitStompIntegrationTest {

	private static final LocalDateTime START_AT = LocalDateTime.of(2099, 12, 20, 18, 0);
	private static final String PROBE_TYPE = "TEST_PROBE";
	private static final long QUIET_MILLIS = 1_000;

	@LocalServerPort
	int port;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	AttendanceService attendanceService;

	@Autowired
	AttendanceWriter attendanceWriter;

	@Autowired
	SettlementConfirmService confirmService;

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
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ApplicationEventPublisher eventPublisher;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SettlementInputAssembler inputAssembler;

	private final BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
	private WebSocketStompClient stompClient;
	private Member host;
	private Member other;
	private Member late;
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		host = saveMember("철수");
		other = saveMember("지수");
		late = saveMember("민수");
		Group group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.saveAll(List.of(join(group, host), join(group, other), join(group, late)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", START_AT, null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		settlementRepository.save(Settlement.createDraft(meeting));
		attendanceRepository.saveAll(List.of(attend(host), attend(other)));
		expenseRepository.save(Expense.create(round, host, 90_000L, "볼링"));
	}

	@AfterEach
	void tearDown() {
		stompClient.stop();
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("커밋된 참석 응답은 구독자에게 ATTENDANCE_CHANGED를 정확히 1건 보낸다")
	void committedWriteSendsOnce() throws Exception {
		subscribeAsHost();

		respond(late, AttendanceStatus.ATTEND);

		assertThat(typesReceivedUntilQuiet()).containsExactly(AttendanceChangedEvent.TYPE);
	}

	@Test
	@DisplayName("바깥 트랜잭션이 롤백된 참석 응답은 메시지를 보내지 않고 행도 남기지 않는다")
	void rolledBackWriteSendsNothing() throws Exception {
		subscribeAsHost();

		// 재시도 facade는 바깥 트랜잭션 안에서 못 부르게 막혀 있어서 트랜잭션 단위인 AttendanceWriter를 직접 부른다
		transactionTemplate.executeWithoutResult(status -> {
			attendanceWriter.respondMine(meeting.getId(), late.getId(), request(AttendanceStatus.ATTEND));
			status.setRollbackOnly();
		});

		assertThat(typesReceivedUntilQuiet()).isEmpty();
		assertThat(attendanceRepository.findAllByMeetingIdAndMemberId(meeting.getId(), late.getId())).isEmpty();
	}

	@Test
	@DisplayName("참석 변경에 밀려 롤백된 재확정은 알림 0건이고, 다시 재확정하면 SETTLEMENT_RECONFIRMED가 1건 온다")
	void lostReconfirmSendsNothing() throws Exception {
		reopenSettlement();
		subscribeAsHost();
		interceptNextAssemble(() -> respond(other, AttendanceStatus.ABSENT));

		assertThatThrownBy(() -> confirmService.confirm(meeting.getId(), host.getId(), true))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(typesReceivedUntilQuiet()).containsExactly(AttendanceChangedEvent.TYPE);
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);

		confirmService.confirm(meeting.getId(), host.getId(), true);

		assertThat(typesReceivedUntilQuiet()).containsExactly(SettlementReconfirmedEvent.TYPE);
	}

	/**
	 * 리비전 1을 확정한 뒤 지출 있는 차수의 참석을 바꿔 REOPENED로 만든다(구독 전이라 이 알림은 받지 않는다).
	 */
	private void reopenSettlement() {
		confirmService.confirm(meeting.getId(), host.getId(), true);
		respond(late, AttendanceStatus.ATTEND);
		assertThat(settlementStatus()).isEqualTo(SettlementStatus.REOPENED);
	}

	/**
	 * 다음 확정 계산 직후에 다른 스레드의 쓰기를 먼저 커밋시킨다(한 번만).
	 */
	private void interceptNextAssemble(Runnable competitor) {
		AtomicBoolean competitorRan = new AtomicBoolean();
		doAnswer(invocation -> {
			Object input = invocation.callRealMethod();
			if (competitorRan.compareAndSet(false, true)) {
				CompletableFuture.runAsync(competitor).orTimeout(30, TimeUnit.SECONDS).join();
			}
			return input;
		}).when(inputAssembler).assemble(eq(meeting.getId()), any(MeetingPenalties.class));
	}

	/**
	 * 호스트로 모임 토픽을 구독하고, 구독이 브로커에 등록된 것을 프로브 이벤트로 확인한 뒤 받은 메시지를 비운다.
	 */
	private void subscribeAsHost() throws Exception {
		StompSession session = connect(host);
		session.subscribe(RealtimeTopics.meeting(meeting.getId()), new StompSessionHandlerAdapter() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((Map<String, Object>) payload);
			}
		});
		awaitProbe();
		Thread.sleep(300);
		received.clear();
	}

	// 구독 등록 시점을 알 수 없으므로 프로브를 받을 때까지 커밋 이벤트를 짧게 반복 발행한다
	private void awaitProbe() throws InterruptedException {
		for (int i = 0; i < 50; i++) {
			transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
					new ProbeEvent(RealtimeTopics.meeting(meeting.getId()), PROBE_TYPE, "probe")));
			Map<String, Object> message = received.poll(100, TimeUnit.MILLISECONDS);
			if (message != null && PROBE_TYPE.equals(message.get("type"))) {
				return;
			}
		}
		throw new IllegalStateException("구독이 등록되지 않았습니다");
	}

	/**
	 * 1초 동안 새 메시지가 없을 때까지 받은 메시지의 type을 순서대로 모은다(프로브는 뺀다).
	 */
	private List<String> typesReceivedUntilQuiet() throws InterruptedException {
		List<String> types = new ArrayList<>();
		Map<String, Object> message;
		while ((message = received.poll(QUIET_MILLIS, TimeUnit.MILLISECONDS)) != null) {
			if (!PROBE_TYPE.equals(message.get("type"))) {
				types.add((String) message.get("type"));
			}
		}
		return types;
	}

	private StompSession connect(Member who) throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.add(HttpHeaders.COOKIE,
				TokenCookieFactory.ACCESS_TOKEN_COOKIE + "=" + jwtTokenProvider.createAccessToken(who.getId()));
		return stompClient.connectAsync("ws://localhost:" + port + WebSocketConfig.ENDPOINT, headers,
				new StompSessionHandlerAdapter() {
				}).get(5, TimeUnit.SECONDS);
	}

	private void respond(Member member, AttendanceStatus status) {
		attendanceService.respondMine(meeting.getId(), member.getId(), request(status));
	}

	private AttendanceRespondRequest request(AttendanceStatus status) {
		return new AttendanceRespondRequest(null, List.of(new RoundAttendanceItem(round.getId(), status)));
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Group group, Member member) {
		return GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER);
	}

	private Attendance attend(Member member) {
		return Attendance.respond(round, member, AttendanceStatus.ATTEND, START_AT);
	}

	private SettlementStatus settlementStatus() {
		return settlementRepository.findByMeetingId(meeting.getId()).orElseThrow().getStatus();
	}

	/** 구독 등록 확인용 테스트 이벤트 */
	record ProbeEvent(String destination, String type, Object data) implements RealtimeEvent {
	}
}
