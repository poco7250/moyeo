package com.poco7250.moyeo.group.dto;

import com.poco7250.moyeo.global.validation.HttpsUrl;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * 내 송금 정보(계좌·송금 링크) 전체 교체 요청. 빈 문자열은 비운 것으로 본다.
 * 송금 링크는 본인이 토스·카카오페이 앱에서 받은 https 링크를 그대로 붙여 넣는다(서버가 만들지 않음, 오픈 이슈 3).
 *
 * @param accountNumber 숫자와 하이픈만, 6~30자. 저장할 때 하이픈을 뺀다
 */
public record PaymentInfoRequest(
		@Size(max = 20, message = "은행 이름은 20자 이하로 입력해 주세요.")
		String bankName,

		@Pattern(regexp = "^$|^[0-9-]{6,30}$", message = "계좌번호는 숫자와 하이픈으로 6~30자 입력해 주세요.")
		String accountNumber,

		@Size(max = 20, message = "예금주는 20자 이하로 입력해 주세요.")
		String accountHolder,

		@HttpsUrl
		String tossLink,

		@HttpsUrl
		String kakaoPayLink) {

	/**
	 * 빈 문자열을 null로, 앞뒤 공백을 지운 값으로 정규화한다. 검증 전에 레코드 생성 시점에 적용된다.
	 */
	public PaymentInfoRequest {
		bankName = normalize(bankName);
		accountNumber = normalize(accountNumber);
		accountHolder = normalize(accountHolder);
		tossLink = normalize(tossLink);
		kakaoPayLink = normalize(kakaoPayLink);
	}

	/**
	 * 하이픈을 뺀 계좌번호. 없으면 null이다.
	 */
	public String accountDigits() {
		return accountNumber == null ? null : accountNumber.replace("-", "");
	}

	/**
	 * 계좌 3항목이 모두 있거나 모두 없는지 검사한다(필드 에러 이름: accountComplete).
	 */
	@AssertTrue(message = "계좌는 은행·계좌번호·예금주를 모두 입력해 주세요.")
	public boolean isAccountComplete() {
		boolean any = bankName != null || accountNumber != null || accountHolder != null;
		return !any || hasAccount();
	}

	/**
	 * 계좌나 송금 링크 중 하나는 있는지 검사한다(필드 에러 이름: anyProvided). 전부 지우려면 DELETE를 쓴다.
	 */
	@AssertTrue(message = "계좌나 송금 링크 중 하나는 입력해 주세요.")
	public boolean isAnyProvided() {
		return hasAccount() || tossLink != null || kakaoPayLink != null;
	}

	private boolean hasAccount() {
		return bankName != null && accountNumber != null && accountHolder != null;
	}

	private static String normalize(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}
}
