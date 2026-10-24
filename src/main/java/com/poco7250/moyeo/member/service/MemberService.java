package com.poco7250.moyeo.member.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.dto.MemberJoinRequest;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관련 비즈니스 로직.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 회원가입. 이메일/닉네임 중복을 확인하고 비밀번호를 암호화해 저장한다.
	 */
	@Transactional
	public MemberResponse join(MemberJoinRequest request) {
		validateDuplicate(request);

		String encodedPassword = passwordEncoder.encode(request.getPassword());
		Member member = Member.create(request.getEmail(), encodedPassword, request.getNickname());
		Member saved = memberRepository.save(member);

		log.info("회원가입 완료: memberId={}", saved.getId());
		return MemberResponse.from(saved);
	}

	/**
	 * 이메일과 닉네임이 이미 쓰이고 있는지 확인한다.
	 */
	private void validateDuplicate(MemberJoinRequest request) {
		if (memberRepository.existsByEmail(request.getEmail())) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		if (memberRepository.existsByNickname(request.getNickname())) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}
	}
}
