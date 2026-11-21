package com.poco7250.moyeo.global.push;

/**
 * 푸시 서비스 응답을 나눈 결과.
 */
public enum PushSendResult {

	/** 푸시 서비스가 받음(2xx) */
	SENT,
	/** 구독이 없어졌거나 만료됨(404·410) — 구독을 지운다 */
	GONE,
	/** 그 밖의 실패(키 오류·서버 오류·타임아웃 등) — 로그만 남긴다 */
	FAILED;

	/**
	 * HTTP 상태 코드로 결과를 고른다.
	 */
	public static PushSendResult fromStatus(int status) {
		if (status >= 200 && status < 300) {
			return SENT;
		}
		if (status == 404 || status == 410) {
			return GONE;
		}
		return FAILED;
	}
}
