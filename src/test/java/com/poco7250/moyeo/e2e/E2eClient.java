package com.poco7250.moyeo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.support.OAuthStateCookies;
import com.poco7250.moyeo.auth.token.TokenCookieFactory;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * E2E·인가 점검 테스트에서 실제 HTTP API를 부르는 클라이언트.
 * 로그인은 카카오 로그인 시작 → 콜백을 실제로 거쳐 받은 access_token 쿠키를 들고 다닌다.
 */
public final class E2eClient {

	private final MockMvc mockMvc;
	private final JsonMapper jsonMapper;

	public E2eClient(MockMvc mockMvc, JsonMapper jsonMapper) {
		this.mockMvc = mockMvc;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * API 응답 한 건. 본문이 JSON이 아니면 body는 비어 있다.
	 */
	public record Exchange(int status, JsonNode body, String location) {

		/** 실패 응답의 error.code */
		public String errorCode() {
			return body.path("error").path("code").asString();
		}
	}

	/**
	 * 카카오 로그인 시작 → 콜백을 거쳐 Access 토큰 쿠키를 받는다. 콜백이 returnUrl로 돌려보내는지도 확인한다.
	 *
	 * @param code 스텁 카카오 클라이언트가 회원 정보로 바꿔 줄 인가 코드
	 */
	public Cookie login(String code, String returnUrl) {
		MockHttpServletResponse start = perform(MockMvcRequestBuilders.get("/api/auth/kakao/login")
				.param("returnUrl", returnUrl));
		assertThat(start.getStatus()).isEqualTo(302);
		Cookie state = start.getCookie(OAuthStateCookies.STATE_COOKIE);
		Cookie returnCookie = start.getCookie(OAuthStateCookies.RETURN_URL_COOKIE);
		assertThat(state).isNotNull();

		MockHttpServletResponse callback = perform(MockMvcRequestBuilders.get("/api/auth/kakao/callback")
				.param("code", code).param("state", state.getValue()).cookie(state, returnCookie));
		assertThat(callback.getStatus()).isEqualTo(302);
		assertThat(callback.getHeader(HttpHeaders.LOCATION)).isEqualTo(returnUrl);
		Cookie access = callback.getCookie(TokenCookieFactory.ACCESS_TOKEN_COOKIE);
		assertThat(access).isNotNull();
		return new Cookie(access.getName(), access.getValue());
	}

	/**
	 * API를 부르고 2xx·success=true를 확인한 뒤 data를 원하는 타입으로 읽는다.
	 */
	public <T> T ok(HttpMethod method, String uri, Cookie session, Object body, Class<T> type) {
		return jsonMapper.treeToValue(okData(method, uri, session, body), type);
	}

	/**
	 * API를 부르고 2xx·success=true를 확인한 뒤 data 노드를 돌려준다.
	 */
	public JsonNode okData(HttpMethod method, String uri, Cookie session, Object body) {
		Exchange exchange = call(method, uri, session, body);
		assertThat(exchange.status()).as("%s %s 응답: %s", method, uri, exchange.body()).isBetween(200, 299);
		assertThat(exchange.body().path("success").asBoolean()).isTrue();
		return exchange.body().path("data");
	}

	/**
	 * API를 부르고 성공·실패와 상관없이 응답을 돌려준다. session이 null이면 쿠키 없이 부른다.
	 * body가 String이면 이미 만든 JSON으로 보고 그대로 보내고, MockMultipartFile이면 multipart 파트로 보낸다.
	 */
	public Exchange call(HttpMethod method, String uri, Cookie session, Object body) {
		AbstractMockHttpServletRequestBuilder<?> request = body instanceof MockMultipartFile file
				? MockMvcRequestBuilders.multipart(method, uri).file(file)
				: MockMvcRequestBuilders.request(method, uri);
		if (session != null) {
			request.cookie(session);
		}
		if (body != null && !(body instanceof MockMultipartFile)) {
			String json = body instanceof String raw ? raw : jsonMapper.writeValueAsString(body);
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		MockHttpServletResponse response = perform(request);
		return new Exchange(response.getStatus(), readJson(response), response.getHeader(HttpHeaders.LOCATION));
	}

	private JsonNode readJson(MockHttpServletResponse response) {
		String contentType = response.getContentType();
		if (contentType == null || !contentType.contains("json")) {
			return jsonMapper.createObjectNode();
		}
		try {
			return jsonMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new IllegalStateException("응답 본문을 JSON으로 읽지 못했습니다", e);
		}
	}

	private MockHttpServletResponse perform(AbstractMockHttpServletRequestBuilder<?> request) {
		try {
			return mockMvc.perform(request).andReturn().getResponse();
		} catch (Exception e) {
			throw new IllegalStateException("API 호출 실패", e);
		}
	}

	/**
	 * 이미 받은 응답에서 쿠키 없이 상태 코드만 필요할 때 쓰는 GET 편의 메서드.
	 */
	public Exchange get(String uri, Cookie session) {
		return call(HttpMethod.GET, uri, session, null);
	}
}
