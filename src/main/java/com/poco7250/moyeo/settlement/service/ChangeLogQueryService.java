package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.dto.ChangeLogPageResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 변경 로그 조회(PRD S-11). 공개 범위는 그룹 멤버 전원이다(오픈 이슈 16 현재 안).
 */
@Service
@RequiredArgsConstructor
public class ChangeLogQueryService {

	// 시간순(같은 시각이면 저장 순)
	private static final Sort CHRONOLOGICAL = Sort.by(Sort.Order.asc(ChangeLog::getChangedAt),
			Sort.Order.asc(ChangeLog::getId));

	private final MeetingAccessChecker meetingAccessChecker;
	private final ChangeLogRepository changeLogRepository;

	/**
	 * 모임의 변경 로그 한 페이지를 오래된 순으로 돌려준다.
	 *
	 * @param page 0부터 시작하는 페이지 번호
	 * @throws BusinessException 그룹 멤버가 아니면(없는 모임 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public ChangeLogPageResponse list(Long meetingId, Long memberId, int page, int size) {
		meetingAccessChecker.requireMember(meetingId, memberId);
		return ChangeLogPageResponse.from(
				changeLogRepository.findAllByMeetingId(meetingId, PageRequest.of(page, size, CHRONOLOGICAL)));
	}
}
