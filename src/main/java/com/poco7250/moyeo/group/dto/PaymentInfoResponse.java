package com.poco7250.moyeo.group.dto;

/**
 * 그룹 멤버의 송금 정보. 계좌번호는 복호화한 평문(숫자만)이다 — 로그·ChangeLog·실시간 메시지에 싣지 않는다.
 *
 * @param bankName 계좌가 없으면 bankName·accountNumber·accountHolder 모두 null
 */
public record PaymentInfoResponse(
		Long memberId,
		String bankName,
		String accountNumber,
		String accountHolder,
		String tossLink,
		String kakaoPayLink) {

	/**
	 * 계좌번호가 로그에 찍히지 않도록 가린 문자열을 돌려준다.
	 */
	@Override
	public String toString() {
		return "PaymentInfoResponse[memberId=" + memberId + ", bankName=" + bankName + ", accountNumber=****"
				+ ", accountHolder=" + accountHolder + "]";
	}
}
