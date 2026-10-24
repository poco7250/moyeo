package com.poco7250.moyeo.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 폼 입력값.
 * Thymeleaf th:field가 getter/setter로 바인딩하므로 record 대신 클래스로 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberJoinRequest {

	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
	private String email;

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요.")
	private String password;

	@NotBlank(message = "비밀번호 확인을 입력해 주세요.")
	private String passwordConfirm;

	@NotBlank(message = "닉네임을 입력해 주세요.")
	@Size(min = 2, max = 20, message = "닉네임은 2~20자로 입력해 주세요.")
	private String nickname;

	/**
	 * 비밀번호와 비밀번호 확인이 같은지 확인한다.
	 */
	public boolean isPasswordMatched() {
		return Objects.equals(password, passwordConfirm);
	}
}
