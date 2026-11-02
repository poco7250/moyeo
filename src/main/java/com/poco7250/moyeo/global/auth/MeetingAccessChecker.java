package com.poco7250.moyeo.global.auth;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모임 리소스 인가 공통 검사. 모임에 딸린 리소스(참석·장소·정산·게임) Service가 진입 시 호출한다.
 * 검사를 통과하면 조회한 모임을 돌려줘 Service가 다시 읽지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingAccessChecker {

	private final MeetingRepository meetingRepository;
	private final GroupAccessChecker groupAccessChecker;

	/**
	 * 회원이 모임이 속한 그룹의 멤버인지 검사하고 모임을 돌려준다.
	 * 없는 모임도 같은 403으로 응답해 모임 존재 여부를 드러내지 않는다.
	 *
	 * @throws BusinessException 없는 모임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	public Meeting requireMember(Long meetingId, Long memberId) {
		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
		// LAZY 프록시의 id 접근이라 그룹 조회 쿼리는 나가지 않는다
		groupAccessChecker.requireMember(meeting.getGroup().getId(), memberId);
		return meeting;
	}

	/**
	 * 회원이 모임의 호스트(생성자)인지 검사하고 모임을 돌려준다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 호스트가 아니면 NOT_MEETING_HOST
	 */
	public Meeting requireHost(Long meetingId, Long memberId) {
		Meeting meeting = requireMember(meetingId, memberId);
		if (!meeting.isHost(memberId)) {
			throw new BusinessException(ErrorCode.NOT_MEETING_HOST);
		}
		return meeting;
	}
}
