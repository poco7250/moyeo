package com.poco7250.moyeo.auth.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 카카오 API 호출용 RestClient 설정.
 * 카카오 장애가 요청 스레드를 오래 붙잡지 않도록 카카오 전용 연결·읽기 타임아웃을 건다.
 */
@Configuration
public class KakaoOAuthConfig {

	/**
	 * 타임아웃을 적용한 카카오 전용 RestClient를 만든다.
	 * 자동 구성된 Builder는 상태를 가지므로 clone해서 다른 클라이언트 설정에 영향을 주지 않는다.
	 */
	@Bean
	RestClient kakaoRestClient(RestClient.Builder builder, KakaoOAuthProperties properties) {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withTimeouts(properties.connectTimeout(), properties.readTimeout());
		return builder.clone()
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}
}
