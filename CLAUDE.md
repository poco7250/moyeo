# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`moyeo`는 Spring Boot 4.1 + Java 21 기반 친구 모임 관리 웹앱의 백엔드 프로젝트다. 제품 요구사항은 `docs/PRD.md`에 있다.

- 빌드 도구: Gradle 9 (wrapper 사용)
- 주요 의존성: Spring Web MVC, Spring Data JPA, Bean Validation, Thymeleaf(OG 메타 HTML용, Task 019), Lombok, DevTools
- DB: MySQL (`mysql-connector-j`)
- 베이스 패키지: `com.poco7250.moyeo`

### 현재 코드 구조

- `global/` — 공통 코드: `BaseTimeEntity`, 설정(`JpaAuditingConfig`, `ClockConfig`, `SchedulingConfig`, `SecurityConfig`), 공통 응답(`ApiResponse`), 예외 처리(`ErrorCode`, `BusinessException`, `GlobalExceptionHandler`)
- `member/` — 카카오 기반 회원(`Member`의 `kakaoId` 유니크). `MemberService.findOrCreateByKakaoId(KakaoProfile)`로 조회·생성, 저장은 `MemberKakaoRegistrar`(`REQUIRES_NEW`). 비밀번호·서버 렌더링 화면은 없다(SPA용 REST API). `MemberController`의 `GET /api/members/me`로 내 정보 조회
- `auth/` — 카카오 로그인과 자체 토큰. `AuthController`(`/api/auth/kakao/login`·`callback`, `/api/auth/refresh`·`logout`), `KakaoOAuthClient`, `JwtTokenProvider`·`TokenService`(Refresh 회전, DB엔 해시만), `TokenCookieFactory`(HttpOnly 쿠키). 인증은 `access_token` 쿠키를 `JwtAuthenticationFilter`가 검증하고, 컨트롤러는 `@LoginMember Long memberId`로 회원 ID를 받는다. 미인증은 `RestAuthenticationEntryPoint`가 401 공통 JSON으로 응답
- 도메인 패키지는 `<도메인>/{domain,dto,repository,service,controller}` 구조를 따른다.
- 진행 상황과 다음 작업은 `docs/ROADMAP.md`를 본다.

## 명령어

이 프로젝트는 Gradle을 쓴다. 전역 설정에 적힌 `npm run ...` 명령은 여기서 해당하지 않는다.

```bash
./gradlew build                 # 컴파일 + 테스트 + jar 생성
./gradlew bootRun               # 애플리케이션 실행
./gradlew test                  # 전체 테스트
./gradlew test --tests "com.poco7250.moyeo.MoyeoApplicationTests"            # 단일 테스트 클래스
./gradlew test --tests "com.poco7250.moyeo.MoyeoApplicationTests.contextLoads" # 단일 테스트 메서드
./gradlew clean
```

별도 린트 설정(Checkstyle, Spotless 등)은 없다.

## 설정 및 환경

- `application.properties`에는 아직 datasource 설정이 없다. MySQL 드라이버와 JPA가 포함돼 있어 DB 접속 정보 없이 실행하거나 `@SpringBootTest`(`contextLoads`)를 돌리면 컨텍스트 로딩이 실패할 수 있다.
- 로컬 DB 접속 정보 같은 개인 설정은 프로젝트 루트의 `config/application-local.properties`에 둔다(gitignore 대상). `local` 프로필로 활성화한다. `src/main/resources`에 두면 jar에 비밀값이 묶이므로 두지 않는다(Spring Boot가 실행 위치의 `./config/`를 자동으로 읽으니 작업 디렉터리는 프로젝트 루트여야 한다). 카카오 키(`kakao.oauth.*`), `auth.jwt.secret`(32바이트 이상), `auth.cookie.secure=false`(http 로컬)도 여기에 둔다.
- 공개 경로는 `SecurityConfig`의 화이트리스트에만 있고, 나머지 API는 전부 인증이 필요하다. 새 공개 API를 만들면 여기에 추가한다.
- 컨트롤러 `@WebMvcTest`는 `@Import(SecurityTestConfig.class)`(`src/test/.../support`)로 실제 시큐리티 체인을 올리고, 인증이 필요하면 `JwtTokenProvider`로 만든 토큰을 `access_token` 쿠키에 실어 보낸다(`MemberControllerTest` 참고).
- `.claude/settings.local.json`도 gitignore 대상이다.

## Spring Boot 4 관련 주의점

Spring Boot 4는 스타터와 자동 구성이 모듈 단위로 쪼개져 있어 3.x와 의존성 이름이 다르다.

- 웹: `spring-boot-starter-webmvc` (3.x의 `starter-web` 아님)
- 테스트 슬라이스도 기능별 테스트 스타터로 분리돼 있다: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-thymeleaf-test`
- 3.x 기준 예제의 import 경로나 어노테이션 패키지가 다를 수 있으므로, API가 불확실하면 Context7로 Boot 4 문서를 확인한다.

## 아키텍처 방침

사용자 전역 지침에 따라 다음 구조를 따른다.

- 레이어드 아키텍처: Controller → Service → Repository
- 계층 간 데이터 전달은 DTO 사용 (엔티티를 API 응답으로 직접 노출하지 않음)
- 생성자 주입 (Lombok `@RequiredArgsConstructor`)
- 트랜잭션 경계는 Service 계층에서 `@Transactional`로 관리
- API 응답 형식과 예외 처리는 일관된 공통 구조로 통일

## Git

- 작업(shrimp task / ROADMAP Task) 하나마다 최신 `dev`에서 `feature/기능명` 브랜치를 새로 만들어 작업하고, 끝나면 `dev`로 `--no-ff` merge한다.
- 다음 작업은 merge된 `dev`에서 다시 새 `feature/` 브랜치를 만든다.
- 파일 수정 후 바로 커밋하지 않는다. 커밋·merge는 사용자가 요청할 때만 한다.
- 커밋 메시지는 한글, 브랜치명은 `feature/기능명` 형식.

## 도구

- `.mcp.json`에 프로젝트 MCP 서버가 등록돼 있다: `idea`(IntelliJ 연동), `context7`(라이브러리 문서), `shrimp-task-manager`(작업 관리, 데이터는 `shrimp_data/`).
- `.claude/settings.json`에서 `java-backend-skills`, `spring-tools` 플러그인을 활성화했다. Spring 관련 변경 후에는 `/spring-tools:validate`로 검증한다.
