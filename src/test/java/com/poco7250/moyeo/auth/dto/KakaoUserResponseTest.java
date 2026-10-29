package com.poco7250.moyeo.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.dto.KakaoUserResponse.KakaoAccount;
import com.poco7250.moyeo.auth.dto.KakaoUserResponse.Profile;
import com.poco7250.moyeo.member.dto.KakaoProfile;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KakaoUserResponseTest {

	@Test
	void 카카오_응답_JSON을_사용자_정보로_변환한다() {
		String json = """
				{
				  "id": 1001,
				  "connected_at": "2026-10-29T00:00:00Z",
				  "kakao_account": {
				    "email": "moyeo@kakao.com",
				    "profile": { "nickname": "모여", "profile_image_url": "https://img.kakao.com/a.png" }
				  }
				}
				""";

		KakaoUserInfo info = JsonMapper.builder().build().readValue(json, KakaoUserResponse.class).toUserInfo();

		assertThat(info).isEqualTo(new KakaoUserInfo(1001L, "모여", "https://img.kakao.com/a.png", "moyeo@kakao.com"));
	}

	@Test
	void 이메일에_동의하지_않으면_email은_null이다() {
		KakaoUserResponse response = new KakaoUserResponse(1001L, new KakaoAccount(null, new Profile("모여", null)));

		KakaoUserInfo info = response.toUserInfo();

		assertThat(info.email()).isNull();
		assertThat(info.nickname()).isEqualTo("모여");
		assertThat(info.profileImageUrl()).isNull();
	}

	@Test
	void 프로필_정보가_없어도_kakaoId만으로_변환한다() {
		KakaoUserInfo withoutProfile = new KakaoUserResponse(1001L, new KakaoAccount("a@kakao.com", null)).toUserInfo();
		KakaoUserInfo withoutAccount = new KakaoUserResponse(1002L, null).toUserInfo();

		assertThat(withoutProfile).isEqualTo(new KakaoUserInfo(1001L, null, null, "a@kakao.com"));
		assertThat(withoutAccount).isEqualTo(new KakaoUserInfo(1002L, null, null, null));
	}

	@Test
	void toProfile은_필드를_그대로_KakaoProfile로_옮긴다() {
		KakaoUserInfo info = new KakaoUserInfo(1001L, "모여", "https://img.kakao.com/a.png", null);

		assertThat(info.toProfile()).isEqualTo(new KakaoProfile(1001L, "모여", "https://img.kakao.com/a.png", null));
	}
}
