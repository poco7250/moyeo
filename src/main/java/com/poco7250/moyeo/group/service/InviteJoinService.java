package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 초대 링크 자동 가입(G-3). 같은 링크를 동시에 눌러도 중복 가입 없이 성공 응답을 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteJoinService {

	private final InviteJoiner inviteJoiner;

	/**
	 * 초대 링크로 그룹에 가입한다. 이미 멤버면 생성 없이 그룹 id를 준다(멱등).
	 * 동시 요청으로 (group, member) 유니크 위반이 나면 먼저 들어간 멤버십을 다시 확인해 돌려준다.
	 * 실패한 트랜잭션 안에서는 재조회할 수 없어서 트랜잭션은 InviteJoiner가 호출마다 따로 연다.
	 */
	public InviteJoinResponse join(String token, Long memberId) {
		try {
			return inviteJoiner.join(token, memberId);
		} catch (DataIntegrityViolationException e) {
			log.info("그룹 동시 가입 감지, 재확인: {}", e.getMostSpecificCause().getClass().getSimpleName());
			return inviteJoiner.join(token, memberId);
		}
	}
}
