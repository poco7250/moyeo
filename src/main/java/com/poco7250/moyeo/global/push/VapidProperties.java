package com.poco7250.moyeo.global.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 웹 푸시 VAPID 설정(push.vapid.*, PRD 6장 PWA 웹 푸시).
 * 키는 application-local.properties나 환경 변수로만 넣는다. 둘 중 하나라도 비어 있으면 푸시 전송을 건너뛴다.
 *
 * @param publicKey      base64url로 인코딩한 P-256 공개키(압축 안 한 65바이트, 프론트 applicationServerKey와 같은 값)
 * @param privateKey     base64url로 인코딩한 P-256 개인키(32바이트, 로그·toString 노출 금지)
 * @param subject        푸시 서비스가 연락할 주소(mailto: 또는 https:)
 * @param connectTimeout 푸시 서비스 연결 타임아웃
 * @param readTimeout    푸시 서비스 읽기 타임아웃
 */
@ConfigurationProperties(prefix = "push.vapid")
public record VapidProperties(
		@DefaultValue("") String publicKey,
		@DefaultValue("") String privateKey,
		@DefaultValue("mailto:admin@moyeo.invalid") String subject,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("5s") Duration readTimeout) {

	/**
	 * 공개키·개인키가 모두 있어야 푸시를 보낼 수 있다.
	 */
	public boolean isConfigured() {
		return !publicKey.isBlank() && !privateKey.isBlank();
	}

	/**
	 * 개인키를 가린 문자열을 돌려준다(설정 덤프·로그로 키가 새지 않도록).
	 */
	@Override
	public String toString() {
		return "VapidProperties[publicKey=" + publicKey + ", privateKey=****, subject=" + subject
				+ ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
	}
}
