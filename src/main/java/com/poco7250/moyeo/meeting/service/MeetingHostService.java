package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.dto.MeetingHostResponse;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 호스트 위임(G-4, 오픈 이슈 15). 호스트가 연락이 안 될 때 방장이 대신 넘길 수도 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingHostService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final GroupMemberRepository groupMemberRepository;
	private final MeetingRepository meetingRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 호스트 권한자(호스트·방장)가 모임 호스트를 다른 그룹 멤버에게 넘긴다. 이미 그 멤버가 호스트면 그대로 돌려준다.
	 * 위임끼리·날짜 확정과 겹치면 Meeting.version 충돌로 늦은 쪽이 409(CONCURRENT_MODIFICATION)로 끝난다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 호스트 권한 없음 NOT_MEETING_HOST,
	 *         대상이 그룹 멤버가 아니면 GROUP_MEMBER_NOT_FOUND
	 */
	@Transactional
	public MeetingHostResponse changeHost(Long meetingId, Long actorId, Long newHostId) {
		Meeting meeting = meetingAccessChecker.requireHost(meetingId, actorId);
		if (!groupMemberRepository.existsByGroupIdAndMemberId(meeting.getGroup().getId(), newHostId)) {
			throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);
		}
		MeetingHostResponse response = new MeetingHostResponse(meetingId, newHostId);
		if (!meeting.changeHost(newHostId)) {
			return response;
		}
		// version 충돌을 이 트랜잭션 안에서 드러내 이벤트가 커밋 실패와 엇갈리지 않게 한다
		meetingRepository.flush();
		log.info("모임 호스트 위임: meetingId={}, actorId={}, newHostId={}", meetingId, actorId, newHostId);
		eventPublisher.publishEvent(new MeetingHostChangedEvent(meetingId, response));
		return response;
	}
}
