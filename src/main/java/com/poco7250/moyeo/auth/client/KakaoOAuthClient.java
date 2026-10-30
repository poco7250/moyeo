package com.poco7250.moyeo.auth.client;

import com.poco7250.moyeo.auth.config.KakaoOAuthProperties;
import com.poco7250.moyeo.auth.dto.KakaoTokenResponse;
import com.poco7250.moyeo.auth.dto.KakaoUserInfo;
import com.poco7250.moyeo.auth.dto.KakaoUserResponse;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 OAuth 클라이언트. 인가 코드로 토큰을 받고, 그 토큰으로 사용자 정보를 조회한다.
 * 인가 코드·clientSecret·액세스 토큰은 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

	private final RestClient kakaoRestClient;
	private final KakaoOAuthProperties properties;

	/**
	 * 카카오 로그인 화면(인가 코드 요청) URL을 만든다.
	 *
	 * @param state CSRF 방지용 state. 콜백에서 쿠키 값과 비교한다
	 */
	public String buildAuthorizeUrl(String state) {
		return UriComponentsBuilder.fromUriString(properties.authorizeUri())
				.queryParam("response_type", "code")
				.queryParam("client_id", properties.clientId())
				.queryParam("redirect_uri", properties.redirectUri())
				.queryParam("state", state)
				.encode()
				.toUriString();
	}

	/**
	 * 인가 코드로 카카오 사용자 정보를 가져온다.
	 *
	 * @throws BusinessException 잘못된·만료된 인가 코드면 KAKAO_AUTH_FAILED, 카카오 장애·타임아웃이면 KAKAO_API_UNAVAILABLE
	 */
	public KakaoUserInfo fetchUserInfo(String authorizationCode) {
		if (!StringUtils.hasText(authorizationCode)) {
			throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
		}
		try {
			String accessToken = requestAccessToken(authorizationCode);
			return requestUserInfo(accessToken).toUserInfo();
		} catch (RestClientException e) {
			// 타임아웃·연결 실패·응답 해석 실패. 메시지에 요청 정보가 섞일 수 있어 예외 타입만 남긴다
			log.warn("카카오 API 호출 실패: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.KAKAO_API_UNAVAILABLE);
		}
	}

	/**
	 * 인가 코드를 카카오 액세스 토큰으로 바꾼다.
	 */
	private String requestAccessToken(String authorizationCode) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("redirect_uri", properties.redirectUri());
		form.add("code", authorizationCode);

		KakaoTokenResponse response = kakaoRestClient.post()
				.uri(properties.tokenUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, res) -> handleError("토큰 요청", res.getStatusCode()))
				.body(KakaoTokenResponse.class);

		if (response == null || !StringUtils.hasText(response.accessToken())) {
			log.warn("카카오 토큰 응답에 access_token이 없음");
			throw new BusinessException(ErrorCode.KAKAO_API_UNAVAILABLE);
		}
		return response.accessToken();
	}

	/**
	 * 액세스 토큰으로 카카오 사용자 정보를 조회한다.
	 */
	private KakaoUserResponse requestUserInfo(String accessToken) {
		KakaoUserResponse response = kakaoRestClient.get()
				.uri(properties.userInfoUri())
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, res) -> handleError("사용자 정보 조회", res.getStatusCode()))
				.body(KakaoUserResponse.class);

		if (response == null || response.id() == null) {
			log.warn("카카오 사용자 정보 응답에 id가 없음");
			throw new BusinessException(ErrorCode.KAKAO_API_UNAVAILABLE);
		}
		return response;
	}

	/**
	 * 카카오 에러 응답을 에러 코드로 바꾼다. 4xx는 인가 코드·토큰 문제, 그 외는 카카오 쪽 장애로 본다.
	 */
	private void handleError(String step, HttpStatusCode status) {
		log.warn("카카오 {} 실패: status={}", step, status.value());
		if (status.is4xxClientError()) {
			throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
		}
		throw new BusinessException(ErrorCode.KAKAO_API_UNAVAILABLE);
	}
}
