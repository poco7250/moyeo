package com.poco7250.moyeo.global.web;

/**
 * 정적 SPA(Task 068)가 쓰는 경로 목록. 화면 forward 설정과 시큐리티 공개 경로가 같이 쓴다.
 * 화면 Task에서 새 화면 경로를 만들면 {@link #SCREEN_PATHS}에 추가한다.
 */
public final class SpaRoutes {

	/** SPA 진입 파일. 화면 경로는 모두 이 파일로 forward 된다 */
	public static final String INDEX = "/index.html";

	/**
	 * 클라이언트 라우터가 그리는 화면 경로. 새로고침·직접 진입 시 서버가 {@link #INDEX}를 준다.
	 * /api·/ws·/share·/actuator와 겹치면 안 된다.
	 */
	public static final String[] SCREEN_PATHS = {
			"/",
			"/login",
			"/invites/*",
			"/groups/**",
			"/meetings/**",
			"/settings",
	};

	/** 정적 파일 경로(src/main/resources/static) */
	public static final String[] STATIC_PATHS = {
			INDEX,
			"/js/**",
			"/css/**",
			"/favicon.ico",
			"/manifest.webmanifest",
			"/sw.js",
	};

	private SpaRoutes() {
	}
}
