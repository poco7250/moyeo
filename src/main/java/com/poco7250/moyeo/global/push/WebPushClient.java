package com.poco7250.moyeo.global.push;

import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 웹 푸시 전송기. 페이로드를 기기 키로 암호화(RFC 8291)하고 VAPID 헤더(RFC 8292)를 붙여 푸시 서비스에 보낸다.
 * VAPID 키가 없으면 아무것도 보내지 않는다(로컬 개발·테스트). 예외를 던지지 않고 결과만 돌려준다.
 */
@Slf4j
@Component
public class WebPushClient {

	private static final String CONTENT_ENCODING = "aes128gcm";

	private final RestClient webPushRestClient;
	private final VapidTokenSigner signer;

	/**
	 * VAPID 설정이 있으면 서명기를 만든다. 키 형식이 틀리면 기동이 실패한다.
	 */
	public WebPushClient(RestClient webPushRestClient, VapidProperties properties, Clock clock) {
		this.webPushRestClient = webPushRestClient;
		this.signer = properties.isConfigured()
				? new VapidTokenSigner(properties.publicKey(), properties.privateKey(), properties.subject(), clock)
				: null;
		if (signer == null) {
			log.warn("VAPID 키가 설정되지 않아 웹 푸시를 보내지 않습니다(push.vapid.public-key/private-key).");
		}
	}

	/**
	 * 푸시를 보낼 수 있는 상태인지(VAPID 키 설정됨) 알려 준다.
	 */
	public boolean isEnabled() {
		return signer != null;
	}

	/**
	 * 기기 하나에 페이로드를 보낸다. 키를 못 읽거나 전송이 실패하면 FAILED, 404·410이면 GONE.
	 *
	 * @param ttl 기기가 꺼져 있을 때 푸시 서비스가 보관할 시간
	 */
	public PushSendResult send(PushTarget target, byte[] payload, Duration ttl) {
		if (signer == null) {
			return PushSendResult.FAILED;
		}
		try {
			byte[] body = WebPushEncryptor.encrypt(payload, EcKeys.decodeBase64Url(target.p256dh()),
					EcKeys.decodeBase64Url(target.auth()));
			return post(target.endpoint(), body, ttl);
		} catch (IllegalArgumentException e) {
			log.warn("웹 푸시 암호화 실패(구독 키 오류): {}", e.getMessage());
			return PushSendResult.FAILED;
		}
	}

	private PushSendResult post(String endpoint, byte[] body, Duration ttl) {
		try {
			int status = webPushRestClient.post()
					.uri(endpoint)
					.header(HttpHeaders.AUTHORIZATION, signer.authorization(endpoint))
					.header(HttpHeaders.CONTENT_ENCODING, CONTENT_ENCODING)
					.header("TTL", String.valueOf(ttl.toSeconds()))
					.header("Urgency", "normal")
					.contentType(MediaType.APPLICATION_OCTET_STREAM)
					.body(body)
					.exchange((request, response) -> response.getStatusCode().value());
			PushSendResult result = PushSendResult.fromStatus(status);
			if (result == PushSendResult.FAILED) {
				log.warn("웹 푸시 전송 실패: host={}, status={}", VapidTokenSigner.origin(endpoint), status);
			}
			return result;
		} catch (RestClientException e) {
			log.warn("웹 푸시 전송 실패: host={}, error={}", VapidTokenSigner.origin(endpoint), e.getMessage());
			return PushSendResult.FAILED;
		}
	}
}
