package com.poco7250.moyeo.global.push;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * {@link PushEndpoint} 검증기. 크롬·엣지(FCM)·파이어폭스·사파리(애플)·윈도우(WNS) 푸시 서비스만 허용한다.
 */
public class PushEndpointValidator implements ConstraintValidator<PushEndpoint, String> {

	private static final List<String> EXACT_HOSTS = List.of("fcm.googleapis.com", "updates.push.services.mozilla.com");
	private static final List<String> HOST_SUFFIXES = List.of(".push.apple.com", ".notify.windows.com");

	/**
	 * null이면 통과, 길이 초과·파싱 실패·https 아님·사용자 정보나 포트 지정·허용 안 된 호스트면 실패.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || isAllowed(value);
	}

	/**
	 * 허용된 푸시 서비스 주소인지 본다.
	 */
	public static boolean isAllowed(String endpoint) {
		if (endpoint.length() > PushEndpoint.MAX_LENGTH) {
			return false;
		}
		try {
			URI uri = new URI(endpoint);
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null && uri.getPort() == -1
					&& uri.getHost() != null && isAllowedHost(uri.getHost().toLowerCase(Locale.ROOT));
		} catch (URISyntaxException e) {
			return false;
		}
	}

	private static boolean isAllowedHost(String host) {
		return EXACT_HOSTS.contains(host) || HOST_SUFFIXES.stream().anyMatch(host::endsWith);
	}
}
