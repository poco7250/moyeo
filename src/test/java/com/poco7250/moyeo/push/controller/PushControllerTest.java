package com.poco7250.moyeo.push.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.dto.PushPreferencesResponse;
import com.poco7250.moyeo.push.dto.PushSubscribeRequest;
import com.poco7250.moyeo.push.dto.PushSubscriptionResponse;
import com.poco7250.moyeo.push.dto.VapidPublicKeyResponse;
import com.poco7250.moyeo.push.service.PushSubscriptionService;
import com.poco7250.moyeo.support.SecurityTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PushController 슬라이스 테스트. 실제 시큐리티 체인으로 쿠키 인증을 거치고, 서비스는 목이다.
 */
@WebMvcTest(controllers = PushController.class)
@Import(SecurityTestConfig.class)
class PushControllerTest {

	private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
	// RFC 8291 부록 A의 기기 키
	private static final String P256DH =
			"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
	private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	PushSubscriptionService pushSubscriptionService;

	@Test
	@DisplayName("공개키는 공통 응답으로 주고, 서버에 키가 없으면 503 PUSH_NOT_CONFIGURED")
	void publicKey() throws Exception {
		given(pushSubscriptionService.getPublicKey()).willReturn(new VapidPublicKeyResponse("BPub"));
		mockMvc.perform(get("/api/push/vapid-public-key").cookie(accessCookie(8L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.publicKey").value("BPub"));

		given(pushSubscriptionService.getPublicKey()).willThrow(new BusinessException(ErrorCode.PUSH_NOT_CONFIGURED));
		mockMvc.perform(get("/api/push/vapid-public-key").cookie(accessCookie(8L)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.PUSH_NOT_CONFIGURED.name()));
	}

	@Test
	@DisplayName("구독 등록은 로그인 회원으로 서비스에 넘기고 키 없이 id·endpoint만 돌려준다")
	void subscribe() throws Exception {
		given(pushSubscriptionService.subscribe(eq(8L), any(PushSubscribeRequest.class)))
				.willReturn(new PushSubscriptionResponse(1L, ENDPOINT, LocalDateTime.of(2099, 12, 1, 12, 0)));

		mockMvc.perform(post("/api/members/me/push-subscriptions").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON).content(subscribeBody(ENDPOINT, P256DH, AUTH)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.endpoint").value(ENDPOINT))
				.andExpect(jsonPath("$.data.p256dh").doesNotExist());
	}

	@ParameterizedTest(name = "{3}")
	@CsvSource({
			"http://fcm.googleapis.com/fcm/send/abc, " + P256DH + ", " + AUTH + ", endpoint",
			"https://evil.example.com/push, " + P256DH + ", " + AUTH + ", endpoint",
			ENDPOINT + ", AAAA, " + AUTH + ", keys.p256dhValid",
			ENDPOINT + ", " + P256DH + ", AAAA, keys.authValid",
			ENDPOINT + ", '', " + AUTH + ", keys.p256dh"})
	@DisplayName("허용 안 된 endpoint·잘못된 키는 400 필드 에러이고 서비스를 부르지 않는다")
	void invalidSubscription(String endpoint, String p256dh, String auth, String field) throws Exception {
		mockMvc.perform(post("/api/members/me/push-subscriptions").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON).content(subscribeBody(endpoint, p256dh, auth)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem(field)));
		verifyNoInteractions(pushSubscriptionService);
	}

	@Test
	@DisplayName("구독 해제는 endpoint를 넘기고 빈 성공 응답을 준다")
	void unsubscribe() throws Exception {
		mockMvc.perform(delete("/api/members/me/push-subscriptions").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		verify(pushSubscriptionService).unsubscribe(8L, ENDPOINT);
	}

	@Test
	@DisplayName("받기 설정 변경은 종류 → 여부 맵을 넘기고 전체 설정을 돌려준다")
	void updatePreferences() throws Exception {
		given(pushSubscriptionService.updatePreferences(8L, Map.of(PushType.MEETING_CREATED, false)))
				.willReturn(PushPreferencesResponse.fromMuted(List.of(PushType.MEETING_CREATED)));

		mockMvc.perform(patch("/api/members/me/push-preferences").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":{\"MEETING_CREATED\":false}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.enabled.MEETING_CREATED").value(false))
				.andExpect(jsonPath("$.data.enabled.ATTENDANCE_REMINDER").value(true));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {"{\"enabled\":{}}", "{\"enabled\":{\"UNKNOWN\":true}}",
			"{\"enabled\":{\"MEETING_CREATED\":null}}"})
	@DisplayName("받기 설정이 비었거나 모르는 종류·null 값이면 400")
	void invalidPreferences(String body) throws Exception {
		mockMvc.perform(patch("/api/members/me/push-preferences").cookie(accessCookie(8L))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.name()));
		verifyNoInteractions(pushSubscriptionService);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이고 서비스를 부르지 않는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(get("/api/members/me/push-preferences")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/push/vapid-public-key")).andExpect(status().isUnauthorized());
		verifyNoInteractions(pushSubscriptionService);
	}

	private static String subscribeBody(String endpoint, String p256dh, String auth) {
		return "{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}".formatted(endpoint, p256dh, auth);
	}

	private Cookie accessCookie(Long memberId) {
		return new Cookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE, jwtTokenProvider.createAccessToken(memberId));
	}
}
