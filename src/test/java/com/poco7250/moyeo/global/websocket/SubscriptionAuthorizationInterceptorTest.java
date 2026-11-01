package com.poco7250.moyeo.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class SubscriptionAuthorizationInterceptorTest {

	private static final String MEETING_TOPIC = "/topic/meetings/10";

	GroupAccessChecker groupAccessChecker = mock(GroupAccessChecker.class);
	MessageChannel channel = mock(MessageChannel.class);
	SubscriptionAuthorizationInterceptor interceptor;

	@BeforeEach
	void setUp() {
		// 모임 10은 그룹 1, 모임 99는 없는 모임(null)으로 해석하는 가짜 해석기
		TopicGroupResolver meetingResolver = new TopicGroupResolver() {
			@Override
			public boolean supports(String destination) {
				return destination.startsWith(RealtimeTopics.MEETING_PREFIX);
			}

			@Override
			public Long resolveGroupId(String destination) {
				return destination.equals(MEETING_TOPIC) ? 1L : null;
			}
		};
		interceptor = new SubscriptionAuthorizationInterceptor(List.of(meetingResolver), groupAccessChecker);
	}

	@Test
	@DisplayName("그룹 멤버는 모임 토픽을 구독할 수 있다")
	void memberCanSubscribe() {
		Message<?> message = frame(StompCommand.SUBSCRIBE, MEETING_TOPIC, user(7L));

		assertThat(interceptor.preSend(message, channel)).isSameAs(message);
		verify(groupAccessChecker).requireMember(1L, 7L);
	}

	@Test
	@DisplayName("비멤버가 모임 토픽을 구독하면 NOT_GROUP_MEMBER로 거부된다")
	void nonMemberRejected() {
		willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER)).given(groupAccessChecker).requireMember(1L, 8L);

		assertErrorCode(frame(StompCommand.SUBSCRIBE, MEETING_TOPIC, user(8L)), ErrorCode.NOT_GROUP_MEMBER);
	}

	@Test
	@DisplayName("없는 모임 토픽은 비멤버와 같은 NOT_GROUP_MEMBER로 거부된다")
	void unknownMeetingRejected() {
		assertErrorCode(frame(StompCommand.SUBSCRIBE, "/topic/meetings/99", user(7L)), ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(groupAccessChecker);
	}

	@Test
	@DisplayName("해석기가 없는 토픽 구독은 기본 거부된다")
	void unsupportedTopicRejected() {
		assertErrorCode(frame(StompCommand.SUBSCRIBE, "/topic/unknown/1", user(7L)),
				ErrorCode.REALTIME_DESTINATION_FORBIDDEN);
	}

	@Test
	@DisplayName("Principal 없이 구독하면 UNAUTHORIZED")
	void anonymousRejected() {
		assertErrorCode(frame(StompCommand.SUBSCRIBE, MEETING_TOPIC, null), ErrorCode.UNAUTHORIZED);
	}

	@Test
	@DisplayName("클라이언트가 /topic 으로 직접 발행하면 거부된다")
	void topicSendRejected() {
		assertErrorCode(frame(StompCommand.SEND, MEETING_TOPIC, user(7L)), ErrorCode.REALTIME_DESTINATION_FORBIDDEN);
	}

	@Test
	@DisplayName("CONNECT와 /app 으로의 SEND는 그대로 통과한다")
	void otherFramesPass() {
		Message<?> connect = frame(StompCommand.CONNECT, null, user(7L));
		Message<?> appSend = frame(StompCommand.SEND, "/app/games/1/ready", user(7L));

		assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);
		assertThat(interceptor.preSend(appSend, channel)).isSameAs(appSend);
		verifyNoInteractions(groupAccessChecker);
	}

	private void assertErrorCode(Message<?> message, ErrorCode expected) {
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}

	/** 핸드셰이크 때 시큐리티가 넣는 것과 같은 형태의 Principal(principal = 회원 ID) */
	private static Principal user(Long memberId) {
		return UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of());
	}

	private static Message<?> frame(StompCommand command, String destination, Principal user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		accessor.setUser(user);
		accessor.setSubscriptionId("sub-0");
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
