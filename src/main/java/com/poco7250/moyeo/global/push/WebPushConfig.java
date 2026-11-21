package com.poco7250.moyeo.global.push;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 푸시 서비스 호출용 RestClient 설정. 느린 푸시 서비스가 전송 스레드를 오래 붙잡지 않게 타임아웃을 건다.
 */
@Configuration
public class WebPushConfig {

	/**
	 * 타임아웃을 적용한 웹 푸시 전용 RestClient를 만든다. 자동 구성 Builder는 clone해서 쓴다.
	 */
	@Bean
	RestClient webPushRestClient(RestClient.Builder builder, VapidProperties properties) {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withTimeouts(properties.connectTimeout(), properties.readTimeout());
		return builder.clone()
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}
}
