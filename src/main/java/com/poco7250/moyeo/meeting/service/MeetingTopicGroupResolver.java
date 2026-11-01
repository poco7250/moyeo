package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.global.websocket.TopicGroupResolver;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모임 토픽({@code /topic/meetings/{meetingId}})을 모임이 속한 그룹으로 해석한다.
 * 형식이 틀리거나(하위 경로·숫자 아님) 없는 모임이면 null을 돌려 구독을 거부하게 한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingTopicGroupResolver implements TopicGroupResolver {

	// Long 범위를 넘지 않도록 최대 18자리까지만 받는다
	private static final Pattern MEETING_ID = Pattern.compile("[1-9]\\d{0,17}");

	private final MeetingRepository meetingRepository;

	/**
	 * 모임 토픽 경로인지 판단한다.
	 */
	@Override
	public boolean supports(String destination) {
		return destination.startsWith(RealtimeTopics.MEETING_PREFIX);
	}

	/**
	 * 모임 토픽이 속한 그룹 ID를 돌려준다. 해석할 수 없으면 null.
	 */
	@Override
	public Long resolveGroupId(String destination) {
		String rawId = destination.substring(RealtimeTopics.MEETING_PREFIX.length());
		if (!MEETING_ID.matcher(rawId).matches()) {
			return null;
		}
		return meetingRepository.findGroupIdById(Long.valueOf(rawId)).orElse(null);
	}
}
