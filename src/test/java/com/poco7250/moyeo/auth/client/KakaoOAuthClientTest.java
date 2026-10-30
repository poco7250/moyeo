package com.poco7250.moyeo.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.poco7250.moyeo.auth.config.KakaoOAuthProperties;
import com.poco7250.moyeo.auth.dto.KakaoUserInfo;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * KakaoOAuthClient를 MockRestServiceServer로 검증한다. 실제 카카오는 호출하지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class KakaoOAuthClientTest {

	private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
	private static final String REDIRECT_URI = "http://localhost:8080/api/auth/kakao/callback";
	private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
	private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
	private static final String CLIENT_SECRET = "dummy-client-secret-xyz";
	private static final String AUTH_CODE = "dummy-auth-code-abc";
	private static final String ACCESS_TOKEN = "dummy-access-token";

	private static final String TOKEN_JSON = """
			{ "access_token": "%s", "token_type": "bearer", "expires_in": 21599 }
			""".formatted(ACCESS_TOKEN);

	private MockRestServiceServer server;
	private KakaoOAuthClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		KakaoOAuthProperties properties = new KakaoOAuthProperties(
				"dummy-client-id", CLIENT_SECRET, REDIRECT_URI,
				AUTHORIZE_URI, TOKEN_URI, USER_INFO_URI, Duration.ofSeconds(3), Duration.ofSeconds(5));
		client = new KakaoOAuthClient(builder.build(), properties);
	}

	@Test
	@DisplayName("인가 URL에 response_type·client_id·redirect_uri·state를 담는다")
	void buildAuthorizeUrl() {
		String url = client.buildAuthorizeUrl("state-abc_123");

		UriComponents uri = UriComponentsBuilder.fromUriString(url).build();
		MultiValueMap<String, String> params = uri.getQueryParams();
		assertThat(uri.getScheme() + "://" + uri.getHost() + uri.getPath()).isEqualTo(AUTHORIZE_URI);
		assertThat(params.getFirst("response_type")).isEqualTo("code");
		assertThat(params.getFirst("client_id")).isEqualTo("dummy-client-id");
		assertThat(UriUtils.decode(params.getFirst("redirect_uri"), StandardCharsets.UTF_8)).isEqualTo(REDIRECT_URI);
		assertThat(params.getFirst("state")).isEqualTo("state-abc_123");
		server.verify();
	}

	@Test
	@DisplayName("토큰·사용자 정보 응답이 성공이면 kakaoId·닉네임·프로필 이미지·이메일을 매핑한다")
	void fetchUserInfoSuccess() {
		expectTokenSuccess();
		server.expect(requestTo(USER_INFO_URI))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
				.andRespond(withSuccess("""
						{
						  "id": 1001,
						  "kakao_account": {
						    "email": "moyeo@kakao.com",
						    "profile": { "nickname": "모여", "profile_image_url": "https://img.kakao.com/a.png" }
						  }
						}
						""", MediaType.APPLICATION_JSON));

		KakaoUserInfo info = client.fetchUserInfo(AUTH_CODE);

		assertThat(info).isEqualTo(new KakaoUserInfo(1001L, "모여", "https://img.kakao.com/a.png", "moyeo@kakao.com"));
		server.verify();
	}

	@Test
	@DisplayName("이메일에 동의하지 않은 응답이면 email은 null이다")
	void fetchUserInfoWithoutEmail() {
		expectTokenSuccess();
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("""
						{ "id": 1002, "kakao_account": { "profile": { "nickname": "모여" } } }
						""", MediaType.APPLICATION_JSON));

		KakaoUserInfo info = client.fetchUserInfo(AUTH_CODE);

		assertThat(info).isEqualTo(new KakaoUserInfo(1002L, "모여", null, null));
		server.verify();
	}

	@Test
	@DisplayName("토큰 요청 본문에 grant_type·client_id·redirect_uri·code를 form으로 담는다")
	void tokenRequestBody() {
		server.expect(requestTo(TOKEN_URI))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().formDataContains(Map.of(
						"grant_type", "authorization_code",
						"client_id", "dummy-client-id",
						"redirect_uri", "http://localhost:8080/api/auth/kakao/callback",
						"code", AUTH_CODE)))
				.andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
		server.expect(requestTo(USER_INFO_URI))
				.andRespond(withSuccess("{ \"id\": 1001 }", MediaType.APPLICATION_JSON));

		client.fetchUserInfo(AUTH_CODE);

		server.verify();
	}

	@ParameterizedTest(name = "토큰 요청 {0}")
	@ValueSource(ints = {400, 401})
	@DisplayName("토큰 요청이 400·401이면 KAKAO_AUTH_FAILED")
	void tokenClientErrorIsAuthFailed(int status) {
		server.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.valueOf(status)));

		assertErrorCode(ErrorCode.KAKAO_AUTH_FAILED);
	}

	@Test
	@DisplayName("토큰 요청이 5xx면 KAKAO_API_UNAVAILABLE")
	void tokenServerErrorIsUnavailable() {
		server.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertErrorCode(ErrorCode.KAKAO_API_UNAVAILABLE);
	}

	@Test
	@DisplayName("사용자 정보 조회가 5xx면 KAKAO_API_UNAVAILABLE")
	void userInfoServerErrorIsUnavailable() {
		expectTokenSuccess();
		server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertErrorCode(ErrorCode.KAKAO_API_UNAVAILABLE);
	}

	@Test
	@DisplayName("카카오 응답이 타임아웃되면 KAKAO_API_UNAVAILABLE")
	void timeoutIsUnavailable() {
		server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertErrorCode(ErrorCode.KAKAO_API_UNAVAILABLE);
	}

	@Test
	@DisplayName("실패해도 clientSecret·인가 코드·액세스 토큰은 로그에 남지 않는다")
	void secretsAreNotLogged(CapturedOutput output) {
		// 토큰 요청 실패(4xx), 사용자 정보 실패(5xx), 타임아웃 경로를 차례로 실행
		server.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		expectTokenSuccess();
		server.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("Read timed out")));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> client.fetchUserInfo(AUTH_CODE)).isInstanceOf(BusinessException.class);
		}

		assertThat(output).contains("카카오");
		assertThat(output).doesNotContain(CLIENT_SECRET, AUTH_CODE, ACCESS_TOKEN);
		server.verify();
	}

	/**
	 * 토큰 요청 성공 응답을 기대 목록에 추가한다.
	 */
	private void expectTokenSuccess() {
		server.expect(requestTo(TOKEN_URI))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
	}

	/**
	 * fetchUserInfo가 주어진 에러 코드의 BusinessException을 던지는지 검증한다.
	 */
	private void assertErrorCode(ErrorCode expected) {
		assertThatThrownBy(() -> client.fetchUserInfo(AUTH_CODE))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(expected);
		server.verify();
	}
}
