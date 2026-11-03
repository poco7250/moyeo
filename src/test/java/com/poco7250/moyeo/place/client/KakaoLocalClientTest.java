package com.poco7250.moyeo.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.config.KakaoLocalProperties;
import com.poco7250.moyeo.place.dto.KakaoLocalSearchResponse;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.hamcrest.Matchers;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * KakaoLocalClient를 MockRestServiceServer로 검증한다. 실제 카카오는 호출하지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class KakaoLocalClientTest {

	private static final String SEARCH_URI = "https://dapi.kakao.com/v2/local/search/keyword.json";
	private static final String REST_KEY = "dummy-local-rest-key";
	private static final String QUERY = "강남 볼링";

	private MockRestServiceServer server;
	private KakaoLocalClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		KakaoLocalProperties properties = new KakaoLocalProperties(REST_KEY, SEARCH_URI,
				Duration.ofSeconds(3), Duration.ofSeconds(3));
		client = new KakaoLocalClient(builder.build(), properties);
	}

	@Test
	@DisplayName("KakaoAK 헤더·인코딩된 검색어로 호출하고 장소 목록·마지막 페이지 여부를 매핑한다")
	void searchSuccess() {
		server.expect(requestTo(Matchers.startsWith(SEARCH_URI)))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "KakaoAK " + REST_KEY))
				.andExpect(queryParam("query", UriUtils.encode(QUERY, StandardCharsets.UTF_8)))
				.andExpect(queryParam("page", "2"))
				.andExpect(queryParam("size", "15"))
				.andRespond(withSuccess("""
						{
						  "documents": [{
						    "id": "26338954",
						    "place_name": "강남볼링장",
						    "category_name": "스포츠,레저 > 볼링장",
						    "address_name": "서울 강남구 역삼동 1",
						    "road_address_name": "서울 강남구 테헤란로 1",
						    "phone": "02-000-0000",
						    "place_url": "http://place.map.kakao.com/26338954",
						    "x": "127.02", "y": "37.49", "distance": ""
						  }],
						  "meta": { "is_end": true, "pageable_count": 1, "total_count": 1 }
						}
						""", MediaType.APPLICATION_JSON));

		KakaoLocalSearchResponse response = client.searchKeyword(QUERY, 2, 15);

		assertThat(response.documents()).containsExactly(new KakaoLocalSearchResponse.Document(
				"26338954", "강남볼링장", "스포츠,레저 > 볼링장", "서울 강남구 역삼동 1", "서울 강남구 테헤란로 1",
				"02-000-0000", "http://place.map.kakao.com/26338954"));
		assertThat(response.meta().isEnd()).isTrue();
		server.verify();
	}

	@ParameterizedTest(name = "카카오 {0}")
	@ValueSource(ints = {400, 401, 403, 429, 500, 503})
	@DisplayName("카카오가 에러 상태(키 오류·쿼터 초과·장애)면 PLACE_SEARCH_UNAVAILABLE")
	void errorStatusIsUnavailable(int status) {
		server.expect(requestTo(Matchers.startsWith(SEARCH_URI))).andRespond(withStatus(HttpStatus.valueOf(status)));

		assertUnavailable();
	}

	@Test
	@DisplayName("타임아웃이면 PLACE_SEARCH_UNAVAILABLE")
	void timeoutIsUnavailable() {
		server.expect(requestTo(Matchers.startsWith(SEARCH_URI)))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertUnavailable();
	}

	@Test
	@DisplayName("실패해도 REST 키·검색어는 로그에 남지 않는다")
	void secretsAreNotLogged(CapturedOutput output) {
		server.expect(requestTo(Matchers.startsWith(SEARCH_URI))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo(Matchers.startsWith(SEARCH_URI)))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> client.searchKeyword(QUERY, 1, 15)).isInstanceOf(BusinessException.class);
		}

		assertThat(output).contains("카카오 로컬");
		assertThat(output).doesNotContain(REST_KEY, QUERY, UriUtils.encode(QUERY, StandardCharsets.UTF_8));
		server.verify();
	}

	/**
	 * 검색 호출이 PLACE_SEARCH_UNAVAILABLE로 실패하는지 확인한다.
	 */
	private void assertUnavailable() {
		assertThatThrownBy(() -> client.searchKeyword(QUERY, 1, 15))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
		server.verify();
	}
}
