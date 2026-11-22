package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingGuest;
import com.poco7250.moyeo.meeting.dto.MeetingGuestResponse;
import com.poco7250.moyeo.meeting.dto.MeetingGuestsResponse;
import com.poco7250.moyeo.meeting.repository.MeetingGuestRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 게스트 추가·삭제(PRD G-6). 호스트 권한자(호스트·방장)만 한다.
 * 같은 모임 요청이 동시에 오면 모임 행 잠금으로 줄 세워 이름 중복·인원 제한을 지킨다.
 */
@Service
@RequiredArgsConstructor
public class MeetingGuestService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final MeetingRepository meetingRepository;
	private final MeetingGuestRepository meetingGuestRepository;
	private final MemberRepository memberRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 모임에 이름만 있는 게스트를 추가한다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 호스트 권한 없음 NOT_MEETING_HOST,
	 *         같은 이름 MEETING_GUEST_DUPLICATED, 20명 초과 MEETING_GUEST_LIMIT
	 */
	@Transactional
	public MeetingGuestResponse add(Long meetingId, Long actorId, String name) {
		Meeting meeting = lockAsHost(meetingId, actorId);
		String strippedName = name.strip();
		List<MeetingGuest> guests = meetingGuestRepository.findAllWithMemberByMeetingId(meetingId);
		if (guests.size() >= MeetingGuest.MAX_PER_MEETING) {
			throw new BusinessException(ErrorCode.MEETING_GUEST_LIMIT);
		}
		if (guests.stream().anyMatch(guest -> guest.name().equals(strippedName))) {
			throw new BusinessException(ErrorCode.MEETING_GUEST_DUPLICATED);
		}
		Member guestMember = memberRepository.save(Member.guest(strippedName));
		MeetingGuest added = meetingGuestRepository.save(MeetingGuest.add(meeting, guestMember, actorId));
		publishChanged(meetingId);
		return MeetingGuestResponse.from(added);
	}

	/**
	 * 모임 게스트를 삭제한다. 게임 참가 이력이 있으면 결과 기록을 지키려고 막는다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 호스트 권한 없음 NOT_MEETING_HOST,
	 *         이 모임 게스트 아님 MEETING_GUEST_NOT_FOUND, 게임 참가 이력 MEETING_GUEST_IN_USE
	 */
	@Transactional
	public void remove(Long meetingId, Long actorId, Long guestId) {
		lockAsHost(meetingId, actorId);
		MeetingGuest guest = meetingGuestRepository.findByMeetingIdAndMemberId(meetingId, guestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_GUEST_NOT_FOUND));
		if (gameParticipantRepository.existsByMemberId(guestId)) {
			throw new BusinessException(ErrorCode.MEETING_GUEST_IN_USE);
		}
		Member guestMember = guest.getMember();
		meetingGuestRepository.delete(guest);
		memberRepository.delete(guestMember);
		publishChanged(meetingId);
	}

	/**
	 * 모임의 현재 게스트 목록(추가 순).
	 */
	@Transactional(readOnly = true)
	public List<MeetingGuestResponse> listOf(Long meetingId) {
		return meetingGuestRepository.findAllWithMemberByMeetingId(meetingId).stream()
				.map(MeetingGuestResponse::from).toList();
	}

	// 인가 검사를 먼저 하고(권한 오류가 잠금 대기보다 먼저 보이게) 모임 행을 잠근다
	private Meeting lockAsHost(Long meetingId, Long actorId) {
		meetingAccessChecker.requireHost(meetingId, actorId);
		return meetingRepository.findByIdForUpdate(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
	}

	private void publishChanged(Long meetingId) {
		eventPublisher.publishEvent(new MeetingGuestsChangedEvent(meetingId,
				new MeetingGuestsResponse(meetingId, listOf(meetingId))));
	}
}
