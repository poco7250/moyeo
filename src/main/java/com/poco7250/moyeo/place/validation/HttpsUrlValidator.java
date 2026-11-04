package com.poco7250.moyeo.place.validation;

import com.poco7250.moyeo.place.domain.Place;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * {@link HttpsUrl} 검증기. 빈 문자열은 링크가 아니므로 실패로 본다(공백 → null 정규화는 요청 DTO 몫).
 */
public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {

	private static final String HTTPS = "https";

	/**
	 * null이면 통과, 길이 초과·파싱 실패·https 아님·호스트 없음이면 실패.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		if (value.length() > Place.MAP_URL_MAX_LENGTH) {
			return false;
		}
		try {
			URI uri = new URI(value);
			return HTTPS.equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
