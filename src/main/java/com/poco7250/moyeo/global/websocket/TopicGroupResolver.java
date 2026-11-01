package com.poco7250.moyeo.global.websocket;

/**
 * 구독 경로(토픽)가 어느 그룹에 속하는지 알려 준다. 도메인마다 하나씩 빈으로 등록한다
 * (예: 모임 토픽은 Task 011, 게임 토픽은 Task 026에서 구현).
 * 맞는 해석기가 없는 토픽은 구독이 거부된다.
 */
public interface TopicGroupResolver {

	/**
	 * 이 해석기가 처리하는 구독 경로인지 판단한다.
	 */
	boolean supports(String destination);

	/**
	 * 구독 경로가 속한 그룹 ID를 돌려준다.
	 *
	 * @return 그룹 ID. 리소스가 없거나 경로 형식이 잘못됐으면 null(구독 거부)
	 */
	Long resolveGroupId(String destination);
}
