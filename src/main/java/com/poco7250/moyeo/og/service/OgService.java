package com.poco7250.moyeo.og.service;

import com.poco7250.moyeo.global.config.ShareLinkProperties;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.config.InviteLinkProperties;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.service.InviteLinkService;
import com.poco7250.moyeo.meeting.config.MeetingLinkProperties;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingShareText;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.og.dto.OgMeta;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 링크의 OG 메타를 만든다. 크롤러가 로그인 없이 읽으므로 토큰을 아는 경우에만 제목·장소·인원을 준다.
 */
@Service
@RequiredArgsConstructor
public class OgService {

	private final MeetingRepository meetingRepository;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final InviteLinkService inviteLinkService;
	private final ShareLinkProperties shareLinkProperties;
	private final MeetingLinkProperties meetingLinkProperties;
	private final InviteLinkProperties inviteLinkProperties;

	/**
	 * 모임 공유 링크의 OG 메타를 만든다.
	 *
	 * @throws BusinessException 없는 토큰이면 MEETING_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public OgMeta meetingMeta(String shareToken) {
		Meeting meeting = meetingRepository.findByShareToken(shareToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
		List<Round> rounds = roundRepository.findAllWithConfirmedPlaceByMeetingId(meeting.getId());
		String description = MeetingShareText.ogDescription(meeting.getStartAt(), MeetingShareText.firstPlaceLabel(rounds),
				countFirstRoundAttendees(rounds), groupMemberRepository.countByGroupId(meeting.getGroup().getId()));
		return new OgMeta(meeting.getTitle(), description, shareLinkProperties.meetingUrl(shareToken),
				meetingLinkProperties.urlOf(meeting.getId()));
	}

	/**
	 * 초대 공유 링크의 OG 메타를 만든다.
	 *
	 * @throws BusinessException 없는 토큰이면 INVITE_LINK_NOT_FOUND, 만료·폐기면 INVITE_LINK_EXPIRED
	 */
	@Transactional(readOnly = true)
	public OgMeta inviteMeta(String token) {
		Group group = inviteLinkService.getUsableLink(token).getGroup();
		String description = "%s 그룹에 초대받았어요 · 멤버 %d명".formatted(
				group.getName(), groupMemberRepository.countByGroupId(group.getId()));
		return new OgMeta(group.getName(), description, shareLinkProperties.inviteUrl(token),
				inviteLinkProperties.urlOf(token));
	}

	private long countFirstRoundAttendees(List<Round> rounds) {
		if (rounds.isEmpty()) {
			return 0;
		}
		return attendanceRepository.countByRoundIdAndStatus(rounds.getFirst().getId(), AttendanceStatus.ATTEND);
	}
}
