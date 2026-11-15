package com.poco7250.moyeo.group.service;

import com.poco7250.moyeo.global.crypto.AesGcmCipher;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupMemberPayment;
import com.poco7250.moyeo.group.dto.PaymentInfoRequest;
import com.poco7250.moyeo.group.dto.PaymentInfoResponse;
import com.poco7250.moyeo.group.repository.GroupMemberPaymentRepository;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 멤버 송금 정보(계좌·송금 링크) 저장·조회(PRD G-5·S-7, 2026-11-15 결정).
 * 본인 정보만 쓰고 읽으며, 다른 멤버 정보는 정산 송금 안내의 받는 사람으로만 내려간다({@link #findPayees}).
 * 금액에 영향이 없어 정산 직렬화·REOPENED·변경 로그·실시간 알림 대상이 아니다.
 * 계좌번호는 여기서 암호화해 저장하고 읽을 때 복호화한다.
 */
@Service
@RequiredArgsConstructor
public class GroupMemberPaymentService {

	private final GroupMemberRepository groupMemberRepository;
	private final GroupMemberPaymentRepository paymentRepository;
	private final AesGcmCipher cipher;

	/**
	 * 내 송금 정보를 조회한다. 없으면 null이다.
	 *
	 * @throws BusinessException 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public PaymentInfoResponse getMine(Long groupId, Long memberId) {
		GroupMember groupMember = requireMembership(groupId, memberId);
		return paymentRepository.findByGroupMemberId(groupMember.getId()).map(this::toResponse).orElse(null);
	}

	/**
	 * 내 송금 정보를 통째로 저장한다. 없으면 만들고 있으면 바꾼다.
	 *
	 * @throws BusinessException 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional
	public PaymentInfoResponse replaceMine(Long groupId, Long memberId, PaymentInfoRequest request) {
		GroupMember groupMember = requireMembership(groupId, memberId);
		String encrypted = request.accountDigits() == null ? null : cipher.encrypt(request.accountDigits());
		Optional<GroupMemberPayment> existing = paymentRepository.findByGroupMemberId(groupMember.getId());
		GroupMemberPayment payment;
		if (existing.isPresent()) {
			payment = existing.get();
			payment.replace(request.bankName(), encrypted, request.accountHolder(), request.tossLink(),
					request.kakaoPayLink());
		} else {
			payment = paymentRepository.save(GroupMemberPayment.create(groupMember, request.bankName(), encrypted,
					request.accountHolder(), request.tossLink(), request.kakaoPayLink()));
		}
		return toResponse(payment);
	}

	/**
	 * 내 송금 정보를 지운다.
	 *
	 * @throws BusinessException 멤버가 아니면 NOT_GROUP_MEMBER, 저장된 정보가 없으면 PAYMENT_INFO_NOT_FOUND
	 */
	@Transactional
	public void deleteMine(Long groupId, Long memberId) {
		GroupMember groupMember = requireMembership(groupId, memberId);
		GroupMemberPayment payment = paymentRepository.findByGroupMemberId(groupMember.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_INFO_NOT_FOUND));
		paymentRepository.delete(payment);
	}

	/**
	 * 정산 송금 안내의 받는 사람들 중 송금 정보가 있는 멤버를 회원 id 순으로 돌려준다. 인가는 호출한 쪽이 이미 했다.
	 */
	@Transactional(readOnly = true)
	public List<PaymentInfoResponse> findPayees(Long groupId, Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return List.of();
		}
		return paymentRepository.findAllByGroupIdAndMemberIdIn(groupId, memberIds).stream()
				.map(this::toResponse)
				.sorted(Comparator.comparing(PaymentInfoResponse::memberId))
				.toList();
	}

	private GroupMember requireMembership(Long groupId, Long memberId) {
		return groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
	}

	private PaymentInfoResponse toResponse(GroupMemberPayment payment) {
		String encrypted = payment.getEncryptedAccountNumber();
		return new PaymentInfoResponse(payment.getGroupMember().getMember().getId(), payment.getBankName(),
				encrypted == null ? null : cipher.decrypt(encrypted), payment.getAccountHolder(),
				payment.getTossLink(), payment.getKakaoPayLink());
	}
}
