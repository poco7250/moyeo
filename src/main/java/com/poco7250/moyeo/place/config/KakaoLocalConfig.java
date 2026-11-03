package com.poco7250.moyeo.place.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 호출용 RestClient 설정.
 * 검색은 사용자가 화면에서 기다리는 요청이라 카카오 장애가 스레드를 오래 붙잡지 않게 전용 타임아웃을 건다.
 */
@Configuration
public class KakaoLocalConfig {

	/**
	 * 타임아웃을 적용한 카카오 로컬 전용 RestClient를 만든다.
	 * 자동 구성된 Builder는 상태를 가지므로 clone해서 다른 클라이언트 설정에 영향을 주지 않는다.
	 */
	@Bean
	RestClient kakaoLocalRestClient(RestClient.Builder builder, KakaoLocalProperties properties) {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withTimeouts(properties.connectTimeout(), properties.readTimeout());
		return builder.clone()
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}
}
