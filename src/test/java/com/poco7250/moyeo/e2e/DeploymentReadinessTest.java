package com.poco7250.moyeo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 배포 전 점검(Task 043·067): 이메일 가입 경로가 완전히 없어졌는지, 비밀값이 설정 파일 밖으로 빠져 있는지,
 * 도커 이미지에 로컬 설정이 안 들어가는지, 운영 스키마가 마이그레이션으로 관리되는지 본다.
 * 실행 위치는 프로젝트 루트(Gradle 기본)라서 src/main/resources와 .gitignore를 파일로 읽는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeploymentReadinessTest {

	/** 운영 jar에 값이 들어가면 안 되는 설정 키 */
	private static final List<String> SECRET_KEYS = List.of("kakao.oauth.client-id", "kakao.oauth.client-secret",
			"kakao.local.rest-api-key", "auth.jwt.secret", "payment.crypto.encryption-key", "push.vapid.private-key");

	/** ${ENV:} 또는 ${ENV:${ENV2:}}처럼 환경 변수만 가리키고 기본값은 비어 있어야 한다 */
	private static final String ENV_ONLY = "\\$\\{[A-Z_]+:(\\$\\{[A-Z_]+:})?}";

	private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

	@Autowired
	ApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	@DisplayName("이메일 가입 경로가 없다: 가입·비밀번호 API, PasswordEncoder 빈, 회원 비밀번호 필드, 가입 전용 에러 코드 모두 없음")
	void emailSignupRemoved() {
		List<String> paths = handlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream())
				.toList();
		assertThat(paths).noneMatch(path -> path.matches("(?i).*(signup|sign-up|register|password|/members/join"
				+ "|/members/new).*"));
		assertThat(context.getBeanNamesForType(PasswordEncoder.class)).isEmpty();
		assertThat(Arrays.stream(Member.class.getDeclaredFields()).map(Field::getName))
				.doesNotContain("password");
		assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
				.doesNotContain("DUPLICATE_EMAIL", "DUPLICATE_NICKNAME");
	}

	@Test
	@DisplayName("서버 렌더링 템플릿은 OG 메타 HTML만 남아 있다(회원가입 폼 뷰 없음)")
	void onlyOgTemplatesRemain() throws IOException {
		try (Stream<Path> templates = Files.walk(MAIN_RESOURCES.resolve("templates"))) {
			assertThat(templates.filter(Files::isRegularFile)
					.map(path -> MAIN_RESOURCES.resolve("templates").relativize(path).toString()))
					.isNotEmpty()
					.allMatch(path -> path.startsWith("og/"));
		}
	}

	@Test
	@DisplayName("비밀값은 application.properties에 환경 변수 자리표시자로만 있고 실제 값은 없다")
	void secretsExternalized() throws IOException {
		Properties properties = load(MAIN_RESOURCES.resolve("application.properties"));

		assertThat(SECRET_KEYS).allSatisfy(key -> assertThat(properties.getProperty(key))
				.as(key).isNotNull().matches(ENV_ONLY));
	}

	@Test
	@DisplayName("운영 리소스에는 프로필별 설정 파일이 없고, 로컬 설정 파일은 gitignore 대상이다")
	void localConfigKeptOutOfJar() throws IOException {
		try (Stream<Path> files = Files.list(MAIN_RESOURCES)) {
			assertThat(files.map(path -> path.getFileName().toString()))
					.noneMatch(name -> name.matches("application-.+\\.(properties|ya?ml)"));
		}
		assertThat(Files.readAllLines(Path.of(".gitignore"), StandardCharsets.UTF_8))
				.map(String::strip)
				.contains("application-local.properties");
	}

	@Test
	@DisplayName("도커 빌드 컨텍스트에서 로컬 설정·데이터가 빠지고, 운영 env 파일은 gitignore 대상이다")
	void deployFilesKeepSecretsOut() throws IOException {
		assertThat(Files.readAllLines(Path.of(".dockerignore"), StandardCharsets.UTF_8))
				.map(String::strip)
				.contains("config/", "data/", ".env");
		assertThat(Files.readAllLines(Path.of(".gitignore"), StandardCharsets.UTF_8))
				.map(String::strip)
				.contains(".env");
		assertThat(Path.of("deploy/.env.example")).isRegularFile();
	}

	@Test
	@DisplayName("운영 스키마는 Flyway가 만들고 Hibernate는 검사만 한다(ddl-auto=validate, V1 베이스라인 존재)")
	void schemaManagedByMigrations() throws IOException {
		Properties properties = load(MAIN_RESOURCES.resolve("application.properties"));

		assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(MAIN_RESOURCES.resolve("db/migration/V1__init.sql")).isRegularFile();
	}

	private static Properties load(Path path) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}
}
