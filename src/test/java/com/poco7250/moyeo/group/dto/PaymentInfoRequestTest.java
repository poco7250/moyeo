package com.poco7250.moyeo.group.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentInfoRequestTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	@DisplayName("계좌 3항목 전부 또는 링크만 있으면 통과하고, 공백은 null로·하이픈은 빼서 쓴다")
	void valid() {
		PaymentInfoRequest account = new PaymentInfoRequest(" 국민 ", "110-123-456789", "철수", "", null);
		PaymentInfoRequest linkOnly = new PaymentInfoRequest(null, " ", null, "https://toss.me/abc", null);

		assertThat(violations(account)).isEmpty();
		assertThat(account.bankName()).isEqualTo("국민");
		assertThat(account.tossLink()).isNull();
		assertThat(account.accountDigits()).isEqualTo("110123456789");
		assertThat(violations(linkOnly)).isEmpty();
		assertThat(linkOnly.accountDigits()).isNull();
	}

	@Test
	@DisplayName("계좌 일부만·전부 비움·http 링크·문자 섞인 계좌번호·길이 초과는 해당 필드 에러")
	void invalid() {
		// 계좌가 반쪽이면 계좌도 링크도 없는 셈이라 두 규칙이 모두 걸린다
		assertThat(violations(new PaymentInfoRequest("국민", "110123456789", null, null, null)))
				.containsExactlyInAnyOrder("accountComplete", "anyProvided");
		assertThat(violations(new PaymentInfoRequest("국민", "110123456789", null, "https://toss.me/a", null)))
				.containsExactly("accountComplete");
		assertThat(violations(new PaymentInfoRequest(null, null, null, null, null))).containsExactly("anyProvided");
		assertThat(violations(new PaymentInfoRequest(null, null, null, "http://toss.me/abc", "javascript:alert(1)")))
				.containsExactlyInAnyOrder("tossLink", "kakaoPayLink");
		assertThat(violations(new PaymentInfoRequest("국민", "110-abc-456", "철수", null, null)))
				.containsExactly("accountNumber");
		assertThat(violations(new PaymentInfoRequest("국".repeat(21), "1234567", "철".repeat(21), null, null)))
				.containsExactlyInAnyOrder("bankName", "accountHolder");
	}

	private static Set<String> violations(PaymentInfoRequest request) {
		return VALIDATOR.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toSet());
	}
}
