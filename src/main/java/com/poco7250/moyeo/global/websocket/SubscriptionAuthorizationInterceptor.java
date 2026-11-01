package com.poco7250.moyeo.global.websocket;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 클라이언트가 보낸 STOMP 프레임을 인가한다(clientInboundChannel 인터셉터).
 * <ul>
 *   <li>SUBSCRIBE: 로그인 회원이고, 토픽을 처리하는 {@link TopicGroupResolver}가 있고, 그 그룹 멤버일 때만 허용</li>
 *   <li>SEND: /topic 으로 직접 발행하는 것은 거부(토픽 발행은 서버만 한다)</li>
 * </ul>
 * 핸드셰이크 인증은 시큐리티 필터 체인이 이미 했으므로(/ws는 인증 필요 경로) 여기서는 Principal만 꺼내 쓴다.
 * 여기서 던진 예외는 STOMP ERROR 프레임으로 바뀌어 클라이언트에 전달된다({@link StompErrorHandler}).
 */
@Component
@RequiredArgsConstructor
public class SubscriptionAuthorizationInterceptor implements ChannelInterceptor {

	private final List<TopicGroupResolver> resolvers;
	private final GroupAccessChecker groupAccessChecker;

	/**
	 * 커맨드별로 인가 검사를 하고, 통과하면 메시지를 그대로 넘긴다.
	 */
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			return message;
		}
		if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
			authorizeSubscribe(accessor.getDestination(), accessor.getUser());
		} else if (StompCommand.SEND.equals(accessor.getCommand())) {
			rejectTopicSend(accessor.getDestination());
		}
		return message;
	}

	/**
	 * 구독 경로가 속한 그룹의 멤버인지 검사한다. 해석할 수 없는 경로는 기본 거부한다.
	 */
	private void authorizeSubscribe(String destination, Principal user) {
		Long memberId = memberIdOf(user);
		if (destination == null) {
			throw new BusinessException(ErrorCode.REALTIME_DESTINATION_FORBIDDEN);
		}
		TopicGroupResolver resolver = resolvers.stream()
				.filter(candidate -> candidate.supports(destination))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.REALTIME_DESTINATION_FORBIDDEN));
		Long groupId = resolver.resolveGroupId(destination);
		if (groupId == null) {
			// 없는 모임·게임도 비멤버와 같은 응답을 줘 존재 여부를 드러내지 않는다
			throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
		}
		groupAccessChecker.requireMember(groupId, memberId);
	}

	/**
	 * 클라이언트가 /topic 으로 직접 발행하면 거부한다.
	 */
	private void rejectTopicSend(String destination) {
		if (destination != null && destination.startsWith(RealtimeTopics.TOPIC_PREFIX)) {
			throw new BusinessException(ErrorCode.REALTIME_DESTINATION_FORBIDDEN);
		}
	}

	/**
	 * 핸드셰이크 때 정해진 Principal에서 회원 ID를 꺼낸다(이름 = memberId 문자열).
	 */
	private Long memberIdOf(Principal user) {
		if (user == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		try {
			return Long.valueOf(user.getName());
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
	}
}
