package com.poco7250.moyeo.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompErrorHandlerTest {

	StompErrorHandler errorHandler = new StompErrorHandler();

	@Test
	@DisplayName("감싸진 BusinessException이면 ERROR 프레임 message에 에러 코드 이름을 담는다")
	void businessExceptionToErrorCode() {
		Message<byte[]> client = clientFrame();
		Throwable ex = new MessageDeliveryException(client, new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		Message<byte[]> error = errorHandler.handleClientMessageProcessingError(client, ex);

		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(error);
		assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
		assertThat(accessor.getMessage()).isEqualTo("NOT_GROUP_MEMBER");
		assertThat(accessor.getReceiptId()).isEqualTo("r-1");
		assertThat(new String(error.getPayload(), StandardCharsets.UTF_8))
				.isEqualTo(ErrorCode.NOT_GROUP_MEMBER.getMessage());
	}

	@Test
	@DisplayName("알 수 없는 예외는 내부 메시지 없이 INTERNAL_ERROR로 감춘다")
	void unknownExceptionHidden() {
		Message<byte[]> error = errorHandler.handleClientMessageProcessingError(
				clientFrame(), new IllegalStateException("내부 정보"));

		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(error);
		assertThat(accessor.getMessage()).isEqualTo("INTERNAL_ERROR");
		assertThat(new String(error.getPayload(), StandardCharsets.UTF_8)).doesNotContain("내부 정보");
	}

	private static Message<byte[]> clientFrame() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/meetings/1");
		accessor.setReceipt("r-1");
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
