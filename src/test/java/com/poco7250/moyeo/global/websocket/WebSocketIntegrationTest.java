package com.poco7250.moyeo.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 서버에 STOMP로 붙어 핸드셰이크 인증·구독 인가·커밋 후 브로드캐스트를 끝까지 확인한다.
 * 모임·게임 해석기는 아직 없으므로 테스트 전용 토픽 /topic/test-groups/{groupId}를 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, WebSocketIntegrationTest.TestTopicConfig.class})
class WebSocketIntegrationTest {

	private static final String TEST_TOPIC_PREFIX = "/topic/test-groups/";

	@LocalServerPort
	int port;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	ApplicationEventPublisher eventPublisher;

	@Autowired
	TransactionTemplate transactionTemplate;

	WebSocketStompClient stompClient;
	Member member;
	Member outsider;
	Group group;

	@BeforeEach
	void setUp() {
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new ByteArrayMessageConverter());
		member = memberRepository.save(Member.ofKakao(2001L, "멤버", null, null));
		outsider = memberRepository.save(Member.ofKakao(2002L, "외부인", null, null));
		group = groupRepository.save(Group.create("실시간 테스트"));
		groupMemberRepository.save(GroupMember.join(group, member, "멤버", GroupRole.OWNER));
	}

	@AfterEach
	void tearDown() {
		stompClient.stop();
		// 서버 트랜잭션이 커밋되므로 직접 정리(FK 순서)
		groupMemberRepository.deleteAllInBatch();
		groupRepository.deleteAllInBatch();
		memberRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("access_token 쿠키 없이 핸드셰이크하면 연결이 거부된다")
	void handshakeWithoutCookieRejected() {
		CompletableFuture<StompSession> future = stompClient.connectAsync(
				url(), new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {
				});

		assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
				.rootCause().hasMessageContaining("401");
	}

	@Test
	@DisplayName("그룹 멤버는 구독 후 커밋된 이벤트를 받는다")
	void memberReceivesCommittedEvent() throws Exception {
		// 서버는 application/json으로 보내므로 JSON 컨버터로 받는다
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		StompSession session = connect(member, new StompSessionHandlerAdapter() {
		});
		CompletableFuture<Map<String, Object>> received = new CompletableFuture<>();
		session.subscribe(topic(), new StompSessionHandlerAdapter() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.complete(toMap(payload));
			}
		});

		// 구독 등록 시점을 알 수 없으므로 받을 때까지 짧게 반복 발행한다
		for (int i = 0; i < 50 && !received.isDone(); i++) {
			transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
					new TestEvent(topic(), "TEST_CHANGED", "안녕")));
			Thread.sleep(100);
		}

		assertThat(received.get(1, TimeUnit.SECONDS))
				.containsEntry("type", "TEST_CHANGED")
				.containsEntry("data", "안녕");
	}

	@Test
	@DisplayName("비멤버가 구독하면 ERROR 프레임(NOT_GROUP_MEMBER)을 받는다")
	void nonMemberSubscriptionRejected() throws Exception {
		CompletableFuture<StompHeaders> error = new CompletableFuture<>();
		StompSession session = connect(outsider, new StompSessionHandlerAdapter() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return byte[].class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				error.complete(headers);
			}
		});

		session.subscribe(topic(), new StompSessionHandlerAdapter() {
		});

		StompHeaders headers = error.get(5, TimeUnit.SECONDS);
		assertThat(headers.getFirst("message")).isEqualTo("NOT_GROUP_MEMBER");
	}

	private StompSession connect(Member who, StompSessionHandlerAdapter handler) throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		String token = jwtTokenProvider.createAccessToken(who.getId());
		headers.add(HttpHeaders.COOKIE, TokenCookieFactory.ACCESS_TOKEN_COOKIE + "=" + token);
		return stompClient.connectAsync(url(), headers, handler).get(5, TimeUnit.SECONDS);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> toMap(Object payload) {
		return (Map<String, Object>) payload;
	}

	private String url() {
		return "ws://localhost:" + port + WebSocketConfig.ENDPOINT;
	}

	private String topic() {
		return TEST_TOPIC_PREFIX + group.getId();
	}

	/** 테스트용 실시간 이벤트 */
	record TestEvent(String destination, String type, Object data) implements RealtimeEvent {
	}

	/** /topic/test-groups/{groupId} 를 그 그룹으로 해석하는 테스트 전용 해석기 */
	@TestConfiguration
	static class TestTopicConfig {

		@Bean
		TopicGroupResolver testGroupTopicResolver() {
			return new TopicGroupResolver() {
				@Override
				public boolean supports(String destination) {
					return destination.startsWith(TEST_TOPIC_PREFIX);
				}

				@Override
				public Long resolveGroupId(String destination) {
					return Long.valueOf(destination.substring(TEST_TOPIC_PREFIX.length()));
				}
			};
		}
	}
}
