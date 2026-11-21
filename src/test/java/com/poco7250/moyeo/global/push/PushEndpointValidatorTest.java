package com.poco7250.moyeo.global.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 푸시 endpoint 허용 목록(SSRF 방지)과 푸시 서비스 응답 분류 단위 테스트.
 */
class PushEndpointValidatorTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"https://fcm.googleapis.com/fcm/send/abc",
			"https://updates.push.services.mozilla.com/wpush/v2/abc",
			"https://web.push.apple.com/QGx",
			"https://wns2-par02p.notify.windows.com/w/?token=abc"})
	@DisplayName("크롬·파이어폭스·사파리·윈도우 푸시 서비스 https 주소는 허용한다")
	void allowsKnownPushServices(String endpoint) {
		assertThat(PushEndpointValidator.isAllowed(endpoint)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://fcm.googleapis.com/fcm/send/abc",
			"https://evil.example.com/fcm.googleapis.com",
			"https://fcm.googleapis.com.evil.example.com/x",
			"https://notpush.apple.com/x",
			"https://fcm.googleapis.com:8080/x",
			"https://user@fcm.googleapis.com/x",
			"https://169.254.169.254/latest",
			"fcm.googleapis.com/x",
			"https://fcm.googleapis.com/ x"})
	@DisplayName("http·다른 호스트·비슷한 이름·포트·사용자 정보·잘못된 URL은 거절한다")
	void rejectsOthers(String endpoint) {
		assertThat(PushEndpointValidator.isAllowed(endpoint)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(ints = {200, 201, 202})
	@DisplayName("2xx는 SENT")
	void sent(int status) {
		assertThat(PushSendResult.fromStatus(status)).isEqualTo(PushSendResult.SENT);
	}

	@ParameterizedTest
	@ValueSource(ints = {404, 410})
	@DisplayName("404·410은 구독이 사라진 GONE, 그 밖의 실패는 FAILED")
	void goneAndFailed(int status) {
		assertThat(PushSendResult.fromStatus(status)).isEqualTo(PushSendResult.GONE);
		assertThat(PushSendResult.fromStatus(400)).isEqualTo(PushSendResult.FAILED);
		assertThat(PushSendResult.fromStatus(413)).isEqualTo(PushSendResult.FAILED);
		assertThat(PushSendResult.fromStatus(503)).isEqualTo(PushSendResult.FAILED);
	}
}
