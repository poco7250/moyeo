package com.poco7250.moyeo.global.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 화면 경로(예: /invites/abc)를 SPA 진입 파일로 forward 한다(Task 068).
 * 클라이언트 라우터 경로를 새로고침하거나 카톡 링크로 바로 열어도 404가 나지 않게 하려는 것이다.
 * 컨트롤러 대신 view controller로 등록해 API 매핑(RequestMappingHandlerMapping)과 섞이지 않는다.
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

	private static final String FORWARD_TO_INDEX = "forward:" + SpaRoutes.INDEX;

	/**
	 * 화면 경로마다 index.html forward를 등록한다.
	 */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		for (String path : SpaRoutes.SCREEN_PATHS) {
			registry.addViewController(path).setViewName(FORWARD_TO_INDEX);
		}
	}
}
