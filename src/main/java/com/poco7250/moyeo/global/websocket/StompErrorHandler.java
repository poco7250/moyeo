package com.poco7250.moyeo.global.websocket;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 클라이언트 프레임 처리 중 난 예외를 STOMP ERROR 프레임으로 바꾼다.
 * message 헤더에는 에러 코드 이름(예: NOT_GROUP_MEMBER)만, 본문에는 사용자용 메시지를 담는다.
 * 기본 구현은 예외 메시지를 그대로 내보내 내부 정보가 새므로, 알 수 없는 예외는 INTERNAL_ERROR로 감춘다.
 */
@Slf4j
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

	/**
	 * 예외 원인을 따라가 비즈니스 에러 코드를 찾고 ERROR 프레임을 만든다.
	 */
	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
		ErrorCode errorCode = findErrorCode(ex);
		if (errorCode == ErrorCode.INTERNAL_ERROR) {
			log.warn("STOMP 메시지 처리 실패: {}", ex.getClass().getSimpleName(), ex);
		}
		StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
		errorAccessor.setMessage(errorCode.name());
		errorAccessor.setLeaveMutable(true);
		StompHeaderAccessor clientAccessor = clientMessage == null ? null
				: MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
		if (clientAccessor != null && clientAccessor.getReceipt() != null) {
			// 클라이언트가 receipt를 요청했으면 어느 프레임의 에러인지 알 수 있게 돌려준다
			errorAccessor.setReceiptId(clientAccessor.getReceipt());
		}
		byte[] payload = errorCode.getMessage().getBytes(StandardCharsets.UTF_8);
		return handleInternal(errorAccessor, payload, ex, clientAccessor);
	}

	/**
	 * 원인 체인에서 BusinessException을 찾는다. 없으면 INTERNAL_ERROR.
	 */
	private ErrorCode findErrorCode(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof BusinessException businessException) {
				return businessException.getErrorCode();
			}
		}
		return ErrorCode.INTERNAL_ERROR;
	}
}
