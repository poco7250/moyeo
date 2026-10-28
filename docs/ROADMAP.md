# moyeo ROADMAP

> 기준 PRD: docs/PRD.md (기준일 2026-10-26, 커밋 `0500d24` "PRD 검증 반영: 벌칙·정산 모델 개정" 이후 상태) · 마지막 갱신: 2026-10-28 (`feature/member-kakao` 기준)

## 개요

- **목표:** "모임 공지 → 장소 정하기 → 참석 → 놀기(게임) → 정산"을 하나의 링크로 끝내는 모바일 웹앱의 백엔드(JSON API + WebSocket) MVP(v0.1) 완성
- **현재 단계:** Phase 0 (기반 정리) 완료. 다음은 Phase 1 Task 004(카카오 OAuth 클라이언트). 코드에는 카카오 기반 회원(`member/`)과 공통 코드(`global/`)만 있고, 인증 API·그룹·모임·장소·게임·정산 도메인은 아직 코드가 없다.
- **구현 순서:** PRD 8장 "MVP 구현 순서(백엔드)" 0 → 1 → 2 → 3 → 4 → 5-a → 6 → 7 → 8 → 5-b → 9를 그대로 따른다. 실시간 기반(WebSocket)은 P-3(4단계)의 수용 기준이 요구하므로 3단계 앞으로 당겼다(Task 010).

| Phase | 이름 | PRD 단계 | Task 수 | 완료 | 상태 |
|---|---|---|---|---|---|
| 0 | 기반 정리 | 0 | 3 | 3 | ✅ 완료 |
| 1 | 카카오 로그인·토큰 | 1 | 3 | 0 | ⬜ 대기 |
| 2 | 그룹·초대 | 2 | 3 | 0 | ⬜ 대기 |
| 3 | 실시간 기반·모임·차수·참석 | 3 | 4 | 0 | ⬜ 대기 |
| 4 | 장소 결정 | 4 | 6 | 0 | ⏸️ 일부 보류 (오픈 이슈 5) |
| 5 | 정산 기본·최초 확정 | 5-a | 6 | 0 | ⬜ 대기 |
| 6 | 게임 방·사다리·꽝뽑기 | 6 | 5 | 0 | ⬜ 대기 |
| 7 | 벌칙자 확정·다시 돌리기 | 7 | 4 | 0 | ⬜ 대기 |
| 8 | 벌칙 정산 연동·확정 게이트 | 8 | 4 | 0 | ⬜ 대기 |
| 9 | 재확정·리비전 비교·알림 | 5-b | 2 | 0 | ⬜ 대기 |
| 10 | MVP 통합 검증 | 9 | 3 | 0 | ⬜ 대기 |
| 11 | v0.2 (개략) | — | 7 | 0 | ⬜ 대기 |
| 12 | v0.3 (개략) | — | 9 | 0 | ⬜ 대기 |
| 13 | v1.0 (개략) | — | 7 | 0 | ⬜ 대기 |
| **합계** | | | **66** (MVP 43) | **3** | |

## 상태 범례

| 상태 | 의미 |
|---|---|
| ⬜ 대기 | 아직 시작 안 함 |
| 🔄 진행 중 | 작업 중 (기존 코드로 일부 항목 충족 포함) |
| ✅ 완료 | 완료 조건 모두 충족 |
| ⏸️ 보류 | 선행 조건(오픈 이슈 등) 미해결 — 사유 필수 |

- ⚠️ = PRD에 모호하거나 누락된 부분이 있어 착수 전 확인이 필요한 항목(PRD는 수정하지 않음)
- 체크박스 `[x]` = 현재 코드(2026-10-28, `feature/member-kakao`)로 이미 충족된 항목

## 공통 규칙 (모든 Task에 적용)

- 패키지: `com.poco7250.moyeo.<도메인>/{domain,dto,repository,service,controller}`, 공통은 `global/`
- Controller → Service → Repository, 요청/응답은 DTO(엔티티 직접 노출 금지), `@RequiredArgsConstructor` 생성자 주입
- 트랜잭션 경계는 Service `@Transactional`, 조회는 `@Transactional(readOnly = true)`
- 에러는 `BusinessException(ErrorCode)`. PRD에 이름이 있는 에러 코드는 그 이름 그대로 쓴다
- 금액은 `long` 정수(원), 부동소수점 금지, 곱셈은 `Math.multiplyExact`
- 실시간 브로드캐스트는 `@TransactionalEventListener(phase = AFTER_COMMIT)`에서만
- 시간 의존 로직(마감, `applyOrderAt`, 제한 시간)은 주입한 `Clock`으로 계산해 테스트에서 고정 가능하게
- 테스트 슬라이스: `@DataJpaTest`(`spring-boot-starter-data-jpa-test`), `@WebMvcTest`(`spring-boot-starter-webmvc-test`). Boot 3.x 스타터 이름 금지
- 명령어: `./gradlew test`, `./gradlew build`

## 의존성 개요

```
Phase 0  001 ─┬─ 002 ── 003
              │
Phase 1       └──────── 003 → 004 → 005 → 006
Phase 2                                   006 → 007 → 008 → 009
Phase 3  006 → 010 (실시간 기반)
         009 → 011 → 012 → 013 (← 010)
Phase 4  012 → 014 → 016 → 017 → 018 → 019 (← 013)
         006 → 015 ─────┘(016이 015 사용)
Phase 5  012 → 020 → 021 → 022 → 024 → 025 (← 013)
                       023 ─────┘(024가 023 사용)
Phase 6  013, 021 → 026 → 027 → 028 / 029 → 030
Phase 7  026 → 031 → 032 (← 028, 029) → 033 → 034
Phase 8  023, 031 → 035;  022, 034 → 036;  035, 036 → 037;  024, 037 → 038
Phase 9  038 → 039 → 040
Phase 10 040 → 041, 042 → 043
```

---

## Phase 0: 기반 정리

- **목표:** PRD 7장 "현재 코드와의 차이 → 전환 계획"을 처리하고, 모든 도메인이 쓰는 공통 응답·예외·테스트 기반을 만든다.
- **포함 기능 ID:** G-3(전환 준비), 6장 아키텍처 방침(공통 응답·예외)

### Task 001: 공통 API 응답 구조와 전역 예외 처리 JSON 전환   ✅ 완료

- **완료일:** 2026-10-27 · 커밋 `7a11c50`(구현), `d1c1e3b`(테스트) · merge `f1d47f1`

- **관련 기능:** 6장 아키텍처 방침(일관된 API 응답·예외 구조), 7장 전환 계획(`GlobalExceptionHandler`, `ErrorCode`)
- **선행 Task:** 없음
- **구현 사항**
  - [x] `global/error/BusinessException`(ErrorCode 보유) — 기존 코드 존재
  - [x] `global/error/ErrorCode` enum(HttpStatus + message) — 기존 코드 존재
  - [x] `global/common/ApiResponse<T>` 공통 래퍼 `{ success, data, error: { code, message, fieldErrors? } }` + 정적 팩토리(`ok(data)`, `ok()`, `fail(errorCode)`, `fail(errorCode, fieldErrors)`)
  - [x] `global/error/ErrorResponse`(code, message, `List<FieldError>`) DTO — record로 구현
  - [x] `GlobalExceptionHandler`를 `@ControllerAdvice` + `ModelAndView` → `@RestControllerAdvice` + `ResponseEntity<ApiResponse<?>>`로 전환
    - [x] `BusinessException` → ErrorCode의 상태·코드
    - [x] `MethodArgumentNotValidException`·`HandlerMethodValidationException`·`ConstraintViolationException` → 400 `INVALID_INPUT` + 필드 에러 목록
    - [x] `HttpMessageNotReadableException`(정수 아닌 금액 등) → 400 `INVALID_INPUT`
    - [x] `DataIntegrityViolationException` → 409 `DUPLICATE_RESOURCE`(기존 처리 유지)
    - [x] `ObjectOptimisticLockingFailureException` → 409 `CONCURRENT_MODIFICATION`
    - [x] 그 외 `Exception` → 500 `INTERNAL_ERROR`(스택트레이스는 로그에만)
    - [x] (추가) `MissingServletRequestParameterException`·`MethodArgumentTypeMismatchException` → 400 `INVALID_INPUT`, `NoResourceFoundException` → 404 `RESOURCE_NOT_FOUND`, `HttpRequestMethodNotSupportedException` → 405 `METHOD_NOT_ALLOWED`
  - [x] `ErrorCode`에 공통 코드 추가: `INVALID_INPUT`(400), `UNAUTHORIZED`(401), `CONCURRENT_MODIFICATION`(409), `INTERNAL_ERROR`(500) + (추가) `RESOURCE_NOT_FOUND`(404), `METHOD_NOT_ALLOWED`(405). `DUPLICATE_EMAIL`·`DUPLICATE_NICKNAME` 제거는 Task 003에서
  - 도메인 에러 코드는 각 Task에서 추가한다(PRD 7장 목록 이름 그대로)
- **테스트**
  - [x] `@WebMvcTest` + 테스트 전용 더미 Controller(`ExceptionTestController`)로 예외 종류별 응답 검증: 상태 코드·`success=false`·`error.code`·필드 에러 목록
  - [x] 성공 응답이 `{ success: true, data: ..., error: null }` 형식인지 검증
  - [x] 500 응답 본문에 예외 메시지·스택트레이스가 노출되지 않는지 검증
- **수락 기준**
  - Given 필수 필드가 빠진 요청 / When 어떤 REST API를 호출하면 / Then 400과 `error.code = INVALID_INPUT`, 필드별 에러 목록을 JSON으로 받는다.
  - Given 낙관적 락 충돌 / When 예외가 Controller 밖으로 나가면 / Then 409 `CONCURRENT_MODIFICATION` JSON을 받는다(HTML 에러 페이지 아님).
- **완료 조건**
  - [x] `./gradlew test` 통과(`GlobalExceptionHandlerTest` 9건 포함 전체 17건) · [x] `/spring-tools:validate` 통과(에러 없음, `MemberController` info 1건은 Task 003에서 제거 예정) · [x] 수락 기준 충족
- **참고:** `contextLoads`는 아직 로컬 MySQL(`application-local.properties`)에 기대어 통과한다 → Task 002에서 해소

### Task 002: 테스트 기반과 설정 정리   ✅ 완료

- **완료일:** 2026-10-28 · 브랜치 `feature/test-infra`


- **관련 기능:** 기술 기반(전 기능 공통)
- **선행 Task:** Task 001
- **구현 사항**
  - [x] `global/common/BaseTimeEntity`(`createdAt`, `updatedAt`) + `global/config/JpaAuditingConfig` — 기존 코드 존재
  - [x] `application.properties`의 `spring.jpa.open-in-view=false`, 기본 프로필 `local` — 기존 설정 존재
  - [x] 테스트 DB 전략 결정 및 적용 — Testcontainers MySQL(`mysql:8.4`) 채택. `spring-boot-testcontainers` + `testcontainers-mysql`·`testcontainers-junit-jupiter`(Testcontainers 2.x), `support/TestcontainersConfiguration`의 `@ServiceConnection`으로 datasource 연결
  - [x] `src/test/resources/application-test.properties`(`create-drop`, `hibernate.jdbc.time_zone=Asia/Seoul`)와 `test` 프로필 — Gradle `test` 태스크의 `spring.profiles.active=test`로 적용(test 리소스에 `application.properties`를 두면 main 설정을 가리므로 금지)
  - [x] `global/config/ClockConfig` — `Clock` 빈(Asia/Seoul) 등록, 시간 의존 로직은 `Clock` 주입
  - [x] `global/config/SchedulingConfig` — `@EnableScheduling`(투표 마감·꽝뽑기 제한 시간용, 실제 사용은 Task 018·030)
  - [x] 테스트 공통 픽스처 위치 정리(`src/test/java/.../support/`): `TestcontainersConfiguration`, 고정 `Clock`(`TestClocks.fixed`), 감사 검증용 `AuditingTestEntity`. 도메인 엔티티 생성 헬퍼는 각 도메인 Task에서 추가
- **테스트**
  - [x] `MoyeoApplicationTests.contextLoads`가 로컬 MySQL 없이 통과(+ `Clock` 빈 시간대 검증)
  - [x] `@DataJpaTest`로 `BaseTimeEntity` 감사 필드가 저장 시 채워지는지 검증(`BaseTimeEntityTest` 2건, 수정 시 `updatedAt` 갱신 포함)
- **수락 기준**
  - Given 로컬 DB 설정이 없는 CI 환경 / When `./gradlew test`를 실행하면 / Then 모든 테스트가 외부 수동 설정 없이 통과한다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 20건) · [x] `/spring-tools:validate` 통과(에러 없음, `MemberController` info 1건은 Task 003에서 제거 예정) · [x] 수락 기준 충족
- **참고:** 테스트 실행에 Docker가 필요하다(CI 러너도 Docker 사용 가능해야 함). DB를 쓰는 테스트는 `@Import(TestcontainersConfiguration.class)`, `@DataJpaTest`는 추가로 `@AutoConfigureTestDatabase(replace = NONE)`와 `JpaAuditingConfig` Import

### Task 003: member 카카오 기반 전환과 이메일 가입 코드 제거   ✅ 완료

- **완료일:** 2026-10-28 · 브랜치 `feature/member-kakao`

- **관련 기능:** G-3, 7장 전환 계획(Member·MemberService·MemberController·MemberJoinRequest·PasswordEncoderConfig·ErrorCode)
- **선행 Task:** Task 001, Task 002
- **구현 사항**
  - [x] `member/domain/Member` 전환: `password` 제거, `kakaoId`(Long, 유니크·필수) 추가, `nickname`(카카오 닉네임, 유니크 제거), `profileImageUrl` 추가, `email` nullable
  - [x] 정적 팩토리 `Member.ofKakao(kakaoId, nickname, profileImageUrl, email)` + `updateKakaoProfile(...)`
  - [x] `MemberRepository.findByKakaoId(Long)`
  - [x] `MemberService.findOrCreateByKakaoId(KakaoProfile)` — 없으면 생성, 있으면 프로필 갱신(로그인마다 카카오 최신 값으로 덮어씀). 입력은 member 소유 `member/dto/KakaoProfile`(auth DTO를 받으면 auth ↔ member 순환이 생겨서), Task 004의 `KakaoUserInfo`가 변환해 넘긴다
    - [x] 동시 최초 로그인: 저장은 `MemberKakaoRegistrar`(`REQUIRES_NEW`)가 하고, `kakaoId` 유니크 위반이면 `MemberService`가 새 트랜잭션으로 1회 재호출해 기존 회원 반환
  - [x] 제거: `MemberService.join`, `MemberController`(`@Controller` 회원가입 폼), `MemberJoinRequest`, `templates/member/*`, `global/config/PasswordEncoderConfig`, `build.gradle`의 `spring-security-crypto`, `ErrorCode.DUPLICATE_EMAIL`·`DUPLICATE_NICKNAME`
  - [x] SPA 결정에 맞춰 `HomeController`·`templates/index.html`·`fragments/layout.html`·`static/css/style.css`·`templates/error/*` 제거(OG 메타 HTML용 Thymeleaf 의존성은 유지 — Task 019). `/`는 이제 404 JSON
  - [x] `member/dto/MemberResponse`를 카카오 기반 필드(id, nickname, profileImageUrl)로 수정
  - [x] 기존 `MemberServiceTest` 교체, `MemberControllerTest` 삭제(REST 컨트롤러는 Task 006)
- **테스트**
  - [x] `@DataJpaTest`(`MemberRepositoryTest`): `kakaoId` 유니크 제약, `email` null 저장 가능, 같은 `nickname` 2명 저장 가능
  - [x] 단위 테스트(`MemberKakaoRegistrarTest`, Repository 목): 신규 `kakaoId` → 생성, 기존 `kakaoId` → 생성 없이 프로필 갱신
  - [x] 단위 테스트(`MemberServiceTest`): `kakaoId` 유니크 위반 발생 시 재조회해 같은 회원 반환, 재시도도 실패하면 예외 전파
  - [x] 통합(`MemberServiceIntegrationTest`): 같은 `kakaoId`로 두 번 호출 시 회원 1건·같은 id
- **수락 기준**
  - Given 처음 보는 카카오 사용자 / When `findOrCreateByKakaoId`를 호출하면 / Then `Member`가 1건 생성되고, 같은 `kakaoId`로 다시 호출하면 추가 생성 없이 같은 회원이 반환된다.
  - Given 전환 완료 코드 / When 프로젝트를 검색하면 / Then `password`, `PasswordEncoder`, `/members/join` 경로가 남아 있지 않다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 23건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족(남은 `password`는 datasource 설정·예외 테스트 더미 문자열뿐)
- **참고:** 로컬 DB는 `ddl-auto=update`라 기존 `member` 테이블의 `password`·`email` NOT NULL 컬럼이 남는다 → 로컬에서 `member` 테이블을 한 번 drop 후 실행

---

## Phase 1: 카카오 로그인·토큰

- **목표:** 카카오 인가 코드 → 회원 조회/생성 → 자체 토큰(Access + Refresh, `HttpOnly` 쿠키) 발급, 이후 모든 API가 기대는 인증 주체(로그인 회원) 확보
- **포함 기능 ID:** G-3, 6장 인증·인가

### Task 004: 카카오 OAuth 클라이언트 (인가 코드 → 사용자 정보)   ⬜ 대기

- **관련 기능:** G-3, 6장 인증
- **선행 Task:** Task 003
- **구현 사항**
  - [ ] `auth/client/KakaoOAuthClient` — 인가 코드로 토큰 요청(`kauth.kakao.com/oauth/token`), 사용자 정보 조회(`kapi.kakao.com/v2/user/me`). `RestClient` 사용(Boot 4 스타터 이름은 Context7로 확인)
  - [ ] `auth/config/KakaoOAuthProperties`(`@ConfigurationProperties`: clientId, clientSecret, redirectUri, 타임아웃) — 비밀값은 `application-local.properties`·환경 변수로만
  - [ ] `auth/dto/KakaoUserInfo`(kakaoId, nickname, profileImageUrl, email?) — 동의 안 한 항목은 null 허용. `toProfile()`로 `member/dto/KakaoProfile`로 변환해 `MemberService`에 넘긴다
  - [ ] 연결·읽기 타임아웃 설정
  - [ ] 에러 코드: `KAKAO_AUTH_FAILED`(401, 잘못된·만료된 인가 코드), `KAKAO_API_UNAVAILABLE`(502/503, 카카오 장애·타임아웃)
- **테스트**
  - [ ] 목 서버(`MockRestServiceServer` 등) 기반: 성공 시 `KakaoUserInfo` 매핑(이메일 없는 응답 포함)
  - [ ] 실패: 토큰 요청 400/401 → `KAKAO_AUTH_FAILED`
  - [ ] 실패: 5xx·타임아웃 → `KAKAO_API_UNAVAILABLE`
  - [ ] 클라이언트 시크릿이 로그에 남지 않는지 확인
- **수락 기준**
  - Given 유효한 인가 코드(스텁) / When 클라이언트를 호출하면 / Then kakaoId·닉네임·프로필 이미지가 담긴 `KakaoUserInfo`를 받는다. Given 카카오 응답 지연 / When 타임아웃이 지나면 / Then `KAKAO_API_UNAVAILABLE` 예외가 난다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 005: 자체 토큰(Access·Refresh) 발급·검증   ⬜ 대기

- **관련 기능:** G-3, 6장 인증
- **선행 Task:** Task 004
- **구현 사항**
  - [ ] 인증 처리 방식 결정·적용 — 권장: `spring-boot-starter-security` 필터 체인 + JWT(라이브러리는 Context7로 Boot 4 호환 확인). CSRF: `HttpOnly` 쿠키 인증이므로 `SameSite` 설정과 CSRF 방어 방식을 함께 정한다
  - [ ] `auth/token/JwtTokenProvider` — Access 발급·검증(subject = memberId), 만료 시간 설정값
  - [ ] `auth/domain/RefreshToken` 엔티티(member, tokenHash, expiresAt) + `RefreshTokenRepository` — 재발급 시 회전(기존 토큰 폐기)
  - [ ] `auth/token/TokenCookieFactory` — `HttpOnly`·`Secure`·`SameSite` 쿠키 생성/삭제
  - [ ] 에러 코드: `INVALID_TOKEN`(401), `EXPIRED_TOKEN`(401), `INVALID_REFRESH_TOKEN`(401)
- **테스트**
  - [ ] 단위: Access 발급 → 검증 성공, 서명 위조·만료 토큰 → 각 에러 코드(고정 `Clock`)
  - [ ] `@DataJpaTest`: `RefreshToken` 저장·조회·삭제, 토큰 원문이 아닌 해시가 저장되는지
  - [ ] 단위: Refresh 회전 — 재발급 후 이전 Refresh로 재요청하면 `INVALID_REFRESH_TOKEN`
- **수락 기준**
  - Given 발급된 Refresh 토큰 / When 재발급을 요청하면 / Then 새 Access·Refresh가 발급되고 이전 Refresh는 더 이상 쓸 수 없다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 006: 인증 API·인증 필터·로그인 후 원래 링크 복귀   ⬜ 대기

- **관련 기능:** G-3, 6장 인증·인가(카카오톡 인앱 브라우저)
- **선행 Task:** Task 005
- **선행 조건:** ⚠️ PRD 오픈 이슈 1(SPA 프레임워크·배포 방식) — 쿠키 도메인·리다이렉트 대상 오리진 설정에 영향. 설정값으로 분리해 두고 결정 후 채운다
- **구현 사항**
  - [ ] `auth/controller/AuthController`(`@RestController`)
    - [ ] `GET /api/auth/kakao/login?returnUrl=` — 카카오 인가 URL로 리다이렉트, `state`에 CSRF 값 + `returnUrl` 저장
    - [ ] `GET /api/auth/kakao/callback` — `state` 검증 → `KakaoOAuthClient` → `MemberService.findOrCreateByKakaoId` → 토큰 쿠키 → `returnUrl`로 리다이렉트
    - [ ] `POST /api/auth/refresh`, `POST /api/auth/logout`(Refresh 삭제·쿠키 만료)
  - [ ] `member/controller/MemberController`(`@RestController`) `GET /api/members/me`
  - [ ] 인증 필터(쿠키 Access 검증) + `@LoginMember Long memberId` 리졸버(또는 `@AuthenticationPrincipal`)
  - [ ] `returnUrl` 검증: 허용 오리진의 상대 경로만 허용(오픈 리다이렉트 방지), 아니면 기본 경로
  - [ ] 공개 경로 화이트리스트: 로그인·콜백·재발급·초대 링크 조회·OG 메타 HTML
- **테스트**
  - [ ] `@WebMvcTest`: 콜백 성공 시 `Set-Cookie`에 `HttpOnly` Access·Refresh, `returnUrl`로 302
  - [ ] `@WebMvcTest`: `state` 불일치 → 401, 외부 도메인 `returnUrl` → 기본 경로로 리다이렉트
  - [ ] `@WebMvcTest`: 토큰 없이 `/api/members/me` → 401 `UNAUTHORIZED` JSON, 유효 토큰 → 내 정보
  - [ ] 카카오 클라이언트는 목 처리(실제 호출 없음)
- **수락 기준**
  - Given 미로그인 사용자가 초대 링크 경로를 `returnUrl`로 로그인 시작 / When 카카오 로그인을 마치면 / Then 토큰 쿠키가 발급되고 원래 초대 링크 경로로 돌아간다(G-3 앞부분).
  - Given 로그인 회원 / When `/api/members/me`를 호출하면 / Then 닉네임·프로필 이미지가 공통 응답 래퍼로 반환된다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족 · [ ] 카카오톡 인앱 브라우저에서 로그인·리다이렉트 수동 확인(9장 리스크)

---

## Phase 2: 그룹·초대

- **목표:** 그룹 생성, 초대 링크 발급·만료·재발급, 링크로 자동 가입, 그룹 멤버 인가(`NOT_GROUP_MEMBER`) 공통화
- **포함 기능 ID:** G-1, G-2, G-3

### Task 007: 그룹·멤버·초대 링크 도메인 골격   ⬜ 대기

- **관련 기능:** G-1, G-2, G-3
- **선행 Task:** Task 006
- **구현 사항**
  - [ ] `group/domain/Group`(name) — 테이블명은 예약어 회피(`groups` 등 `@Table` 지정)
  - [ ] `group/domain/GroupMember`(group, member, nickname, emoji, role) — `(group, member)` 유니크 제약. `role`은 G-4(P1) 대비 컬럼만, MVP 로직 없음
  - [ ] `group/domain/InviteLink`(group, token 유니크, expiresAt, revokedAt?) — `isUsable(now)`
  - [ ] Repository: `GroupRepository`, `GroupMemberRepository.existsByGroupIdAndMemberId`, `findAllByGroupId`, `InviteLinkRepository.findByToken`
  - [ ] 토큰 생성기 `InviteTokenGenerator`(`SecureRandom`, URL-safe)
- **테스트**
  - [ ] `@DataJpaTest`: `(group, member)` 유니크 위반 시 `DataIntegrityViolationException`
  - [ ] `@DataJpaTest`: token 유니크, `findByToken`
  - [ ] 단위: `InviteLink.isUsable` — 만료 직전·만료 시각 정각·만료 후, 재발급으로 폐기된 링크
- **수락 기준**
  - Given 같은 그룹·회원 / When `GroupMember`를 두 번 저장하면 / Then 두 번째는 유니크 제약 위반이다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 008: 그룹 생성·초대 링크 발급/재발급·그룹 멤버 인가 공통화   ⬜ 대기

- **관련 기능:** G-1, 6장 인가
- **선행 Task:** Task 007
- **구현 사항**
  - [ ] `group/service/GroupService.create` — 그룹 생성 + 생성자 `GroupMember`(닉네임 기본값 = 카카오 닉네임) + 초대 링크 발급, 한 트랜잭션
  - [ ] `InviteLinkService.reissue` — 기존 링크 폐기(`revokedAt`) + 새 토큰 발급
  - [ ] ⚠️ 초대 링크 만료 기간 기본값·재발급 권한(멤버 누구나 vs 생성자) — PRD 미정. 만료 기간은 설정값으로 두고 결정 후 채운다
  - [ ] `global/auth/GroupAccessChecker.requireMember(groupId, memberId)` → 403 `NOT_GROUP_MEMBER` (이후 모든 그룹 리소스 Service가 사용)
  - [ ] `GroupController`: `POST /api/groups`, `POST /api/groups/{groupId}/invite-links`(재발급), `GET /api/groups`(내 그룹 목록)
  - [ ] DTO: `GroupCreateRequest`(name `@NotBlank`), `GroupResponse`, `InviteLinkResponse`(token, url, expiresAt)
  - [ ] 에러 코드: `NOT_GROUP_MEMBER`(403), `GROUP_NOT_FOUND`(404)
- **테스트**
  - [ ] 단위(`GroupService`): 생성 시 생성자가 멤버가 되고 만료 시각 있는 링크가 발급됨
  - [ ] 단위: 재발급 후 이전 토큰은 `isUsable=false`
  - [ ] 단위(`GroupAccessChecker`): 비멤버 → `NOT_GROUP_MEMBER`
  - [ ] `@WebMvcTest`: 그룹 이름 누락 400 + 필드 에러, 생성 성공 응답 형식
- **수락 기준**
  - Given 로그인한 사용자 / When 그룹을 생성하면 / Then 생성자는 그 그룹 멤버가 되고 만료 시각이 있는 초대 링크 토큰이 발급된다. Given 초대 링크를 재발급하면 / Then 이전 토큰으로 접속 시 "만료된 링크" 응답을 받는다. (PRD G-1)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 009: 초대 링크 자동 가입·멤버 목록·그룹 프로필   ⬜ 대기

- **관련 기능:** G-2, G-3
- **선행 Task:** Task 008
- **구현 사항**
  - [ ] `GET /api/invites/{token}` (비로그인 허용) — 그룹 이름 등 초대 진입 화면용 정보, 만료·폐기면 410 또는 400 `INVITE_LINK_EXPIRED`
  - [ ] `POST /api/invites/{token}/join` (로그인 필요) — 이미 멤버면 생성 없이 그룹 id 반환(멱등), 아니면 `GroupMember` 생성(닉네임 기본값 = 카카오 닉네임). 동시 클릭은 `(group, member)` 유니크 위반을 잡아 기존 멤버로 응답
  - [ ] `GET /api/groups/{groupId}/members` — 그룹 내 닉네임·프로필 이모지 목록(비멤버 403 `NOT_GROUP_MEMBER`)
  - [ ] `PATCH /api/groups/{groupId}/members/me` — 그룹 닉네임·이모지 변경(`@NotBlank`, 길이 제한). ⚠️ 이모지 기본값·허용 범위는 PRD 미정
  - [ ] 에러 코드: `INVITE_LINK_EXPIRED`, `INVITE_LINK_NOT_FOUND`(404)
- **테스트**
  - [ ] 단위(`InviteJoinService`): 신규 가입 / 이미 멤버 재진입(중복 생성 없음) / 만료 링크 거부
  - [ ] 단위: 유니크 위반 예외 발생 시 기존 멤버로 성공 응답
  - [ ] `@DataJpaTest`: 멤버 목록 조회 쿼리
  - [ ] `@WebMvcTest`: 비멤버 멤버 목록 조회 403 `NOT_GROUP_MEMBER`, 닉네임 공백 400
- **수락 기준**
  - Given 미로그인 사용자가 유효한 초대 링크로 접속 / When 카카오 로그인을 마치면 / Then 원래 초대 링크로 돌아가 그룹에 자동 가입되고 그룹 닉네임 기본값은 카카오 닉네임이다. Given 이미 가입된 멤버 / When 같은 링크로 다시 들어오면 / Then 중복 가입 없이 그룹 홈으로 이동한다. (PRD G-3)
  - Given 그룹 멤버 / When 멤버 목록을 조회하면 / Then 각 멤버의 그룹 내 닉네임·프로필 이모지가 반환되고, 비멤버가 조회하면 403(`NOT_GROUP_MEMBER`)을 받는다. (PRD G-2)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 3: 실시간 기반·모임·차수·참석

- **목표:** WebSocket(STOMP) 공통 기반, 모임 생성(호스트 = 생성자, `Settlement` `DRAFT` 동시 생성), 차수 템플릿, 차수별 참석 응답·마감·현황판
- **포함 기능 ID:** M-1, M-2, M-3, M-4, 6장 실시간

### Task 010: WebSocket(STOMP) 기반과 커밋 후 브로드캐스트 공통 구성   ⬜ 대기

- **관련 기능:** 6장 실시간(참석·장소 투표·게임·정산 공통 채널), P-3·R-0 전제
- **선행 Task:** Task 006
- **구현 사항**
  - [ ] 의존성 `spring-boot-starter-websocket`(Boot 4 이름 Context7 확인), `global/websocket/WebSocketConfig`(`@EnableWebSocketMessageBroker`, 엔드포인트 `/ws`, 브로커 `/topic`, 앱 prefix `/app`)
  - [ ] 핸드셰이크 인증: 쿠키 Access 토큰 검증 → STOMP `Principal`(memberId)
  - [ ] 구독 인가 `ChannelInterceptor`: `/topic/meetings/{meetingId}/**`, `/topic/games/{gameId}` 구독 시 그룹 멤버 검사
  - [ ] `global/event/RealtimeEvent` 기반 타입 + `RealtimeBroadcaster`(`@TransactionalEventListener(phase = AFTER_COMMIT)` → `SimpMessagingTemplate`)
  - [ ] 토픽 규약 문서화(클래스 주석): 참석·장소·정산 = 모임 토픽, 게임 = 게임 토픽
- **테스트**
  - [ ] 단위: 구독 인터셉터 — 비멤버 구독 거부, 멤버 허용
  - [ ] 통합(`@SpringBootTest`): 트랜잭션 커밋 시에만 `SimpMessagingTemplate` 호출, 롤백 시 호출 없음(메시징 템플릿 목)
- **수락 기준**
  - Given 이벤트를 발행한 트랜잭션이 롤백 / When 처리가 끝나면 / Then 어떤 브로드캐스트도 나가지 않는다. Given 비멤버 / When 모임 토픽을 구독하면 / Then 거부된다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 011: 모임·차수·참석·정산(골격) 도메인   ⬜ 대기

- **관련 기능:** M-1, M-2, M-3, S-10(정산 상태 골격), D4
- **선행 Task:** Task 009
- **구현 사항**
  - [ ] `meeting/domain/Meeting`(group, title, startAt, memo, responseDeadline?, hostMemberId) + `isHost(memberId)`
  - [ ] `meeting/domain/Round`(meeting, seq, name/kind, placeStatus `UNDECIDED/VOTING/CONFIRMED`, placeVoteDeadline?, confirmedCandidate?, `@Version version`) — 장소 상태 전이 메서드는 Task 014·016에서
  - [ ] `meeting/domain/Attendance`(round, member, status `ATTEND/ABSENT/UNDECIDED`, checkedIn, respondedAt) — `(round, member)` 유니크
  - [ ] `settlement/domain/Settlement`(meeting 유니크, status `DRAFT/CONFIRMED/REOPENED`, currentRevisionNo?, firstConfirmedAt?, `@Version version`) — 생성 시 `DRAFT`. 전이 메서드는 Task 021·024
  - [ ] Repository 4종 + `RoundRepository.findAllByMeetingIdOrderBySeq`
- **테스트**
  - [ ] `@DataJpaTest`: `(round, member)` 유니크, `Settlement.meeting` 유니크
  - [ ] 단위: `Meeting.isHost`
- **수락 기준**
  - Given 한 모임 / When `Settlement`를 두 건 저장하면 / Then 유니크 제약 위반이다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 012: 모임 생성·차수 템플릿·호스트 권한 공통화   ⬜ 대기

- **관련 기능:** M-1, M-2, D4
- **선행 Task:** Task 011
- **구현 사항**
  - [ ] `meeting/service/MeetingService.create` — 그룹 멤버 검사 → `Meeting`(hostMemberId = 요청자) + 차수 목록 + `Settlement(DRAFT)`, 한 트랜잭션
  - [ ] `RoundTemplate` enum(예: "밥→볼링", "밥→보드게임", "밥→볼링→보드게임", 직접 구성) — ⚠️ 템플릿 목록은 PRD 예시만 있음, 확정 필요
  - [ ] `global/auth/MeetingAccessChecker` — `requireMember(meetingId, memberId)`(그룹 멤버), `requireHost(meetingId, memberId)` → 403 `NOT_MEETING_HOST`
  - [ ] `MeetingController`: `POST /api/groups/{groupId}/meetings`, `GET /api/meetings/{meetingId}`(상세: 차수·장소 상태·호스트 여부), `GET /api/groups/{groupId}/meetings`(다가오는/지난 모임)
  - [ ] DTO: `MeetingCreateRequest`(title `@NotBlank`, startAt `@NotNull`, memo, responseDeadline?, template 또는 rounds 목록), `MeetingDetailResponse`(모임 상세 링크 포함)
  - [ ] 에러 코드: `NOT_MEETING_HOST`(403), `MEETING_NOT_FOUND`(404), `ROUND_NOT_FOUND`(404)
- **테스트**
  - [ ] 단위(`MeetingService`): 생성 시 hostMemberId = 생성자, `Settlement` `DRAFT` 1건, 템플릿 "밥→볼링" → 차수 2개 seq 1·2, 모두 `UNDECIDED`
  - [ ] 단위(`MeetingAccessChecker`): 비멤버 `NOT_GROUP_MEMBER`, 호스트 아님 `NOT_MEETING_HOST`
  - [ ] `@WebMvcTest`: 제목·날짜 누락 400 + 필드 에러
- **수락 기준**
  - Given 그룹 멤버 / When 제목·날짜·시간·메모로 모임을 생성하면 / Then 모임이 그룹에 등록되고 모임 상세 링크가 반환되며, 생성자가 `Meeting.hostMemberId`로 저장되고 `Settlement`가 `DRAFT`로 함께 생성된다. 제목·날짜 누락 시 400과 필드 에러를 받는다. (PRD M-1)
  - Given 모임 생성 화면 / When "밥→볼링" 템플릿을 고르면 / Then 1차(밥)·2차(볼링) 차수가 순서대로 생성되고, 각 차수의 장소 상태는 `UNDECIDED`다. (PRD M-2)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 013: 차수별 참석 응답·마감·현황판   ⬜ 대기

- **관련 기능:** M-3, M-4
- **선행 Task:** Task 010, Task 012
- **구현 사항**
  - [ ] `AttendanceService.respond(meetingId, memberId, 차수별 응답 | 단축 옵션)` — `(round, member)` upsert(차수당 1건)
  - [ ] 단축 옵션 `LATE_FROM_SECOND`("늦참(2차부터)") / `FIRST_ONLY`("1차만") → 차수별 상태로 펼치기. ⚠️ 차수 3개 이상일 때 의미(예: 늦참 = 1차 불참·나머지 참석)는 PRD에 2차수 예시만 있음
  - [ ] 마감 검사: `now > responseDeadline`이면 본인 수정 거부(403, `ATTENDANCE_DEADLINE_PASSED`), 호스트의 대리 수정(`PUT /api/meetings/{id}/attendances/{memberId}`)은 허용
  - [ ] 현황판 `GET /api/meetings/{id}/attendance-board` — 차수별 참석/불참/미정/미응답(그룹 멤버 중 응답 없음) 목록
  - [ ] 응답 저장 커밋 후 `AttendanceChangedEvent` 브로드캐스트(Task 010)
  - [ ] 지출 있는 차수의 정산 직렬화·`REOPENED`·재시도 facade 연결은 Task 025에서(5-a 범위)
- **테스트**
  - [ ] 단위: 단축 옵션 펼치기 — 차수 2개에서 늦참 = (불참, 참석), 1차만 = (참석, 불참)
  - [ ] 단위: 재응답 시 기존 행 갱신(새 행 없음)
  - [ ] 단위: 마감 경계 — 마감 시각 정각·1초 후(고정 `Clock`), 호스트 대리 수정 허용, 비호스트 403
  - [ ] `@DataJpaTest`: 현황판 쿼리(미응답자 = 그룹 멤버 − 응답자)
  - [ ] `@WebMvcTest`: 응답 요청 검증·응답 형식
- **수락 기준**
  - Given 차수가 2개인 모임 / When 멤버가 "늦참(2차부터)"을 고르면 / Then 1차는 불참, 2차는 참석으로 저장된다. "1차만"은 그 반대로 저장된다. 같은 멤버가 다시 응답하면 기존 응답이 갱신된다(차수당 1건). (PRD M-3)
  - Given 응답 마감 시간이 지난 모임 / When 멤버가 응답을 바꾸려 하면 / Then 거부된다(호스트 = 모임 생성자의 대리 수정은 허용, 그 외 멤버 403). 현황판은 차수별로 참석/불참/미정/미응답 멤버 목록을 보여준다. (PRD M-4)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 4: 장소 결정

- **목표:** 카카오 로컬 실시간 프록시 검색, 후보 등록(저장은 `kakaoPlaceId` + label만), 바로 확정 / 투표 / 마감 자동 확정, 지도·길찾기 URL 링크와 OG 노출
- **포함 기능 ID:** P-1, P-2, P-3, P-4, P-5
- **⚠️ 착수 전 필수:** PRD 오픈 이슈 5(카카오 로컬 데이터 저장 범위) 카카오 공식 문의 회신 확인. label 저장이 불가로 회신되면 Task 014·016·019 구현 사항이 바뀐다.

### Task 014: 장소 값 타입·후보·투표 도메인과 지도 링크 생성   ⏸️ 보류

- **보류 사유:** 오픈 이슈 5 회신 전 — `Place.label` 저장 가능 여부가 값 타입 설계를 결정
- **관련 기능:** P-1, P-2, P-3, P-5
- **선행 Task:** Task 012
- **선행 조건:** PRD 오픈 이슈 5
- **구현 사항**
  - [ ] `place/domain/Place` `@Embeddable`(kakaoPlaceId?, label, manualAddress?, manualMapUrl?) — 카카오 응답 필드(주소·좌표·카테고리·전화) 컬럼 없음
  - [ ] `place/domain/PlaceCandidate`(round, place, proposedBy), `PlaceVote`(candidate, member) — `(candidate, member)` 유니크
  - [ ] `Round` 장소 상태 전이 메서드: `confirmDirectly(candidate)`(UNDECIDED→CONFIRMED), `openVoting(deadline)`(UNDECIDED→VOTING, CONFIRMED→VOTING), `confirm(candidate)`(VOTING→CONFIRMED). 허용 안 된 전이는 409 `ROUND_PLACE_INVALID_TRANSITION`
  - [ ] `place/validation/@HttpsUrl` 검증기 — `https` 스킴만, 최대 2,048자
  - [ ] `place/domain/PlaceLinkFactory`(순수) — 지도 `https://map.kakao.com/link/map/{id}`, 길찾기 `/link/to/{id}`, 직접 입력은 `manualMapUrl` 또는 `/link/search/{URL 인코딩(manualAddress ?: label)}`
- **테스트**
  - [ ] 단위(`@HttpsUrl`): `https://…` 통과, `http://…`·`javascript:…`·`ftp://…` 실패, 2,048자 통과·2,049자 실패, null 통과(선택 입력)
  - [ ] 단위(`PlaceLinkFactory`): kakaoPlaceId 링크, manualMapUrl 우선, manualAddress 검색어, label 검색어, 한글·공백 URL 인코딩
  - [ ] 단위(`Round` 전이): 허용 전이 3종 성공, 그 외 `ROUND_PLACE_INVALID_TRANSITION`
  - [ ] `@DataJpaTest`: `(candidate, member)` 유니크, `Place` 임베디드 매핑(카카오 장소면 manual* null)
- **수락 기준**
  - Given 직접 입력 / When `manualMapUrl`이 `http://…`·`javascript:…`이거나 2,048자를 넘으면 / Then 검증에 실패한다(API에서는 400 + `manualMapUrl` 필드 에러, Task 016).
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 015: 카카오 로컬 검색 실시간 프록시   ⬜ 대기

- **관련 기능:** P-1, 6장 외부 API
- **선행 Task:** Task 006
- **구현 사항**
  - [ ] `place/client/KakaoLocalClient` — 키워드 검색 REST 호출(REST 키는 서버 설정에만), 타임아웃
  - [ ] `PlaceSearchService.search(query, page)` — **캐싱·저장 없음**, 응답을 DTO로 그대로 전달(`PlaceSearchResponse`: kakaoPlaceId, 이름, 주소 등 표시용)
  - [ ] 서버 측 호출량 제한(사용자별 빈도 제한) — ⚠️ 한도 수치 PRD 미정, 설정값으로
  - [ ] 실패·쿼터 초과·타임아웃 → 503 `PLACE_SEARCH_UNAVAILABLE`(응답에 직접 입력 폴백 안내 플래그), 빈도 초과 → 429 `PLACE_SEARCH_RATE_LIMITED`
  - [ ] `GET /api/places/search?query=` (로그인 필요)
- **테스트**
  - [ ] 목 서버: 성공 매핑, 401/429(쿼터)/5xx/타임아웃 → `PLACE_SEARCH_UNAVAILABLE`
  - [ ] 단위: 같은 검색어 2회 → 카카오 2회 호출(캐시 없음), 어떤 Repository도 호출하지 않음
  - [ ] `@WebMvcTest`: 응답 본문·헤더에 REST 키 없음, 빈 검색어 400
- **수락 기준**
  - Given 검색어 "강남 볼링" / When 장소를 검색하면 / Then 서버가 매 요청마다 카카오 로컬 API를 실시간 호출해 결과 목록을 반환하고(클라이언트 응답·네트워크에 REST 키 없음), 검색 결과는 DB·캐시에 저장되지 않는다. Given 카카오 API 실패·쿼터 초과 / When 검색하면 / Then 직접 입력 폴백 안내를 받는다. (PRD P-1)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 016: 후보 등록·바로 확정·투표 열기   ⏸️ 보류

- **보류 사유:** 오픈 이슈 5 회신 전(label 저장)
- **관련 기능:** P-1, P-2
- **선행 Task:** Task 013, Task 014, Task 015
- **선행 조건:** PRD 오픈 이슈 5
- **구현 사항**
  - [ ] `PlaceCandidateService.add(roundId, request)` — 카카오 장소(kakaoPlaceId + label) 또는 직접 입력(label 필수 + manualAddress·manualMapUrl 선택). `VOTING` 차수에서 그룹 멤버 누구나. ⚠️ `UNDECIDED` 상태에서 후보 추가 가능 여부 PRD 미정
  - [ ] `RoundPlaceService.confirmDirectly`(호스트) — 후보 1개짜리 `PlaceCandidate` 생성 + `CONFIRMED`
  - [ ] `RoundPlaceService.openVoting`(호스트) — `placeVoteDeadline` 필수(`@NotNull @Future`)
  - [ ] ⚠️ 재투표(`CONFIRMED → VOTING`) 시 기존 후보·표 유지/초기화 PRD 미정 — 결정 후 구현
  - [ ] Controller: `POST /api/rounds/{roundId}/candidates`, `POST /api/rounds/{roundId}/place/confirm-direct`, `POST /api/rounds/{roundId}/place/voting`
  - [ ] 변경 후 `PlaceChangedEvent` 브로드캐스트
- **테스트**
  - [ ] 단위: 바로 확정 → 후보 1건 + `CONFIRMED` + `confirmedCandidate` 설정, 비호스트 403 `NOT_MEETING_HOST`
  - [ ] 단위: 투표 열기 → `VOTING` + 마감 시각 저장, 마감 누락 400
  - [ ] `@WebMvcTest`: 직접 입력 `manualMapUrl` http/javascript/2,049자 → 400 `manualMapUrl` 필드 에러, label 누락 400
  - [ ] `@DataJpaTest`: 카카오 후보 저장 시 manual* null
- **수락 기준**
  - When 결과 하나를 후보로 등록하면 / Then `PlaceCandidate`에는 `kakaoPlaceId`와 사용자가 확정한 label만 저장되고(`manualAddress`·`manualMapUrl` null) 카카오 응답의 주소·좌표·카테고리는 어디에도 저장되지 않는다. Given 직접 입력 / When `manualMapUrl`이 `http://…`·`javascript:…`이거나 2,048자를 넘으면 / Then 400과 `manualMapUrl` 필드 에러를 받고 후보는 저장되지 않는다. (PRD P-1)
  - Given `UNDECIDED` 차수 / When 호스트가 장소를 바로 지정하면 / Then 후보 1개짜리 `PlaceCandidate`가 생성되고 차수는 `CONFIRMED`가 된다. When 호스트가 투표를 열면 / Then 차수는 `VOTING`이 되고 마감 시각이 필수로 저장되며, 그룹 멤버 누구나 후보를 추가할 수 있다. (PRD P-2)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 017: 후보 투표·실시간 득표 현황   ⬜ 대기

- **관련 기능:** P-3
- **선행 Task:** Task 016
- **선행 조건:** PRD 오픈 이슈 4(복수 선택 vs 1인 1표 — 현재 안 복수 선택, 수용 기준도 복수 선택 기준), 오픈 이슈 6(불참자 투표 허용 — 현재 안 그룹 멤버 누구나). 착수 전 현재 안 유지 확인
- **구현 사항**
  - [ ] `PlaceVoteService.vote(candidateId, memberId)` — `VOTING` 차수만(아니면 409 `ROUND_PLACE_INVALID_TRANSITION`), 이미 투표했으면 그대로(멱등), 동시 중복은 `(candidate, member)` 유니크 위반을 잡아 성공 처리
  - [ ] ⚠️ 투표 취소(un-vote) 지원 여부 PRD 미정
  - [ ] `GET /api/rounds/{roundId}/votes` — 후보별 득표 수·투표자 목록(공개)
  - [ ] 커밋 후 `VoteChangedEvent`(후보별 득표 수·투표자) 브로드캐스트
- **테스트**
  - [ ] 단위: 후보 2곳 투표 → 2표, 같은 후보 재투표 → 1표 유지
  - [ ] `@DataJpaTest`: 유니크 위반 발생 확인
  - [ ] 단위: 커밋 후 이벤트 페이로드에 득표 수·투표자 목록 포함
- **수락 기준**
  - Given `VOTING` 차수 / When 멤버가 후보 2곳에 투표하면 / Then 두 표가 저장되고 WebSocket으로 득표 수·투표자 목록이 모든 접속자에게 갱신된다. 같은 후보에 다시 투표하면 표는 1개로 유지된다(유니크 제약). (PRD P-3)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 018: 투표 마감 자동 확정·호스트 수동 확정·재투표   ⬜ 대기

- **관련 기능:** P-4
- **선행 Task:** Task 017
- **구현 사항**
  - [ ] `PlaceVoteCloser`(`@Scheduled`) — 마감 지난 `VOTING` 차수 조회 → 차수별 별도 트랜잭션으로 마감 처리
  - [ ] 순수 함수 `VoteTally.decide(후보별 득표)` → 단독 최다 / 동점 후보 목록. ⚠️ 득표 0건(전원 0표)은 동점으로 볼지 PRD 미정
  - [ ] 단독 최다 → `CONFIRMED`, 동점 → `VOTING` 유지 + 호스트 선택 요청 이벤트
  - [ ] `RoundPlaceService.confirmManually(roundId, candidateId)`(호스트, 언제든) / `reopenVoting`(호스트, `CONFIRMED → VOTING`)
  - [ ] 동시성: `Round.version` 낙관적 락 — 마감 처리와 수동 확정 중 늦은 쪽 409 `CONCURRENT_MODIFICATION`(재시도 대상 아님)
- **테스트**
  - [ ] 단위(`VoteTally`): 단독 최다, 2개 동점, 3개 동점
  - [ ] 단위: 마감 처리 — 단독 최다 확정, 동점 유지 + 이벤트, 이미 `CONFIRMED`면 아무것도 안 함
  - [ ] 통합(DB): 같은 차수에 마감 처리와 수동 확정을 동시 실행 → 하나만 반영, 다른 쪽 409
  - [ ] 단위: 비호스트 수동 확정·재투표 403
- **수락 기준**
  - Given 마감 시각이 지난 `VOTING` 차수, 최다 득표 후보가 1개 / When 마감 처리가 실행되면 / Then 그 후보로 `CONFIRMED` 된다. 동점이면 `VOTING`을 유지한 채 호스트에게 선택을 요청한다. 마감 처리와 호스트 수동 확정이 동시에 들어오면 하나만 반영되고 다른 요청은 충돌 응답을 받는다. (PRD P-4)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 019: 확정 장소 노출·OG 메타 HTML   ⏸️ 보류

- **보류 사유:** 오픈 이슈 5 회신 전(label을 OG·공유 문구에 쓰는 것이 문의 범위 ②)
- **관련 기능:** P-5, 6장 공유(OG 태그)
- **선행 Task:** Task 013, Task 018
- **선행 조건:** PRD 오픈 이슈 5, 오픈 이슈 1(OG 경로와 SPA 라우팅·배포 방식)
- **구현 사항**
  - [ ] 모임 상세 응답에 차수별 확정 장소(label, 지도·길찾기 링크 — `PlaceLinkFactory`) 추가
  - [ ] `og/controller/OgController`(`@Controller`, Thymeleaf) — 초대·모임 링크 경로에서 OG 메타(title, description, url)만 담은 HTML + SPA로 이동
  - [ ] OG 설명 문구 생성기(순수): 예 "토요일 모임 · 강남 OO식당 · 5/8명 참석". ⚠️ "5/8명"의 기준(어느 차수 참석자 / 그룹 멤버 수) PRD 미정
  - [ ] 카카오톡 공유 문구 생성(모임 정보 + 확정 장소 label)
- **테스트**
  - [ ] 단위(문구 생성기): 1차 확정 장소 label 포함, 장소 미정 시 문구
  - [ ] `@WebMvcTest`: OG HTML에 `og:description`·`og:title` 메타 포함, 카카오 API 목이 호출되지 않음
  - [ ] 단위: 모임 상세의 길찾기 링크 `https://map.kakao.com/link/to/{kakaoPlaceId}`, 직접 입력 장소의 manualMapUrl / `/link/search/` 대체
- **수락 기준**
  - Given 1차 장소가 확정된 모임 / When 모임 링크의 OG 메타를 요청하면 / Then 미리보기 설명에 확정 장소의 label이 포함되고, 모임 상세에 `https://map.kakao.com/link/to/{kakaoPlaceId}` 길찾기 링크가 노출된다(카카오 API 호출 없이 저장된 ID로 생성). 직접 입력 장소는 `manualMapUrl`이 있으면 그 링크, 없으면 `https://map.kakao.com/link/search/{manualAddress 또는 label}` 링크가 노출된다(API 호출·저장 없음). (PRD P-5)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 5: 정산 기본·최초 확정 (PRD 5-a)

- **목표:** 리비전이 1개만 있어도 되는 모든 것 — 지출·개별 조정, 벌칙 없는 정산 계산·송금 최소화, 정산 직렬화, 최초 확정(리비전 1 스냅샷), 확정 후 금액 영향 변경 시 `REOPENED`, `ChangeLog` 공통 헬퍼, 락 충돌 재시도 facade
- **포함 기능 ID:** S-1, S-2, S-3, S-5, S-8, S-10(최초 확정), S-11(`REOPENED`·`ChangeLog`)
- 이 Phase 직후엔 `REOPENED`에서 재확정할 수 없다(내부 개발 단계 허용, MVP 배포는 Phase 9 이후 — PRD 8장).

### Task 020: 지출·개별 조정·변경 로그 도메인   ⬜ 대기

- **관련 기능:** S-1, S-3, S-11
- **선행 Task:** Task 012
- **구현 사항**
  - [ ] `settlement/domain/Expense`(round, payer, amount `long`, memo) — 생성 시 1~1억 검증 메서드
  - [ ] `settlement/domain/ExpenseShare`(expense, member, amount?, weight?, excluded) — ⚠️ 고정 금액·가중치·제외를 함께 쓸 때의 계산 의미는 PRD 미정(Task 023 참고)
  - [ ] `settlement/domain/ChangeLog`(meeting, actorId, at, entityType, entityId, changeType, beforeJson?, afterJson?, settlementStatusAfter) — 수정·삭제 메서드 없음(append-only)
  - [ ] Repository 3종(`ChangeLogRepository`는 save·조회만 노출)
- **테스트**
  - [ ] `@DataJpaTest`: 매핑, 지출 삭제 시 `ExpenseShare` 함께 삭제(cascade/orphanRemoval)
  - [ ] 단위: `Expense` 금액 0·1·100,000,000·100,000,001 경계
- **수락 기준**
  - Given 금액 100,000,001원 / When `Expense`를 만들면 / Then 검증 실패한다. `ChangeLog`에는 수정 경로가 없다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 021: 정산 직렬화·`REOPENED` 전이·`ChangeLog` 헬퍼·락 충돌 재시도 facade   ⬜ 대기

- **관련 기능:** S-11, 6장 트랜잭션·동시성(정산 직렬화, 재시도 규칙 4차 m7)
- **선행 Task:** Task 010, Task 020
- **선행 조건:** PRD 오픈 이슈 17(`REOPENED` 판단 기준 — 현재 안 "변경 종류 기준"), 오픈 이슈 16(`ChangeLog` 공개 범위·필드 화이트리스트) — 착수 전 현재 안 유지 확인
- **구현 사항**
  - [ ] `SettlementRepository.findByMeetingIdForUpdate` — `@Lock(OPTIMISTIC_FORCE_INCREMENT)`
  - [ ] `settlement/service/SettlementGuard`
    - [ ] `lockForAmountChange(meetingId)` — 직렬화용 강제 버전 증가 잠금
    - [ ] `markAmountChanged(meetingId, actor)` — `CONFIRMED`면 `REOPENED`(같은 트랜잭션), `REOPENED`·`DRAFT`면 유지 → `SettlementReopenedEvent`(커밋 후 "재확정 필요" 브로드캐스트)
    - [ ] `lockOnly(meetingId)` — 금액형 게임 취소용(직렬화만, `REOPENED` 없음, 5차 m7)
  - [ ] `Settlement` 전이 메서드: `reopen()`(CONFIRMED→REOPENED, REOPENED 유지, DRAFT 유지)
  - [ ] `settlement/service/ChangeLogRecorder.record(meetingId, actor, entity, changeType, before, after)` — 같은 트랜잭션 insert, JSON 직렬화는 화이트리스트 DTO만
  - [ ] `global/concurrency/OptimisticRetryExecutor` — 트랜잭션 **바깥** facade에서 최대 2회 재시도, `ObjectOptimisticLockingFailureException`만 대상(유니크 위반·`BusinessException` 제외), 소진 시 409 `CONCURRENT_MODIFICATION`
- **테스트**
  - [ ] 단위(`Settlement`): DRAFT→유지, CONFIRMED→REOPENED, REOPENED→유지
  - [ ] 통합(DB): 같은 모임에 금액 영향 쓰기 2건 동시 → 하나 성공, 다른 쪽 `ObjectOptimisticLockingFailureException`
  - [ ] 통합: 변경 트랜잭션 롤백 시 `ChangeLog`도 없음, 커밋 시 같은 트랜잭션에서 1건
  - [ ] 단위(`OptimisticRetryExecutor`): 1회 실패 후 성공, 3회 연속 실패 → `CONCURRENT_MODIFICATION`, `DataIntegrityViolationException`·`BusinessException`은 재시도 없이 전파, 매 시도가 새 트랜잭션
- **수락 기준**
  - Given `CONFIRMED` 정산 / When 금액 영향 쓰기가 `markAmountChanged`를 호출하면 / Then 같은 트랜잭션에서 `REOPENED`가 되고 커밋 후에만 "재확정 필요" 이벤트가 나간다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 022: 지출 등록·수정·삭제·개별 조정 API   ⬜ 대기

- **관련 기능:** S-1, S-3, S-11
- **선행 Task:** Task 021
- **구현 사항**
  - [ ] `ExpenseService.create/update/delete` — 그룹 멤버 권한, `SettlementGuard.lockForAmountChange` + `markAmountChanged`, `ChangeLogRecorder`(항상 기록)
  - [ ] 대상 지출 삭제 시 `PenaltyRule` 처리는 Task 037에서 연결(이 시점엔 규칙 없음)
  - [ ] `ExpenseShareService.adjust(expenseId, 조정 목록)` — 제외·고정 금액·가중치, 같은 직렬화·로그
  - [ ] 결제자는 그룹 멤버여야 함(아니면 400). ⚠️ 결제자가 차수 불참자일 수 있는지 PRD 미정
  - [ ] DTO: `ExpenseRequest`(amount `@NotNull @Min(1) @Max(100_000_000)` `Long`, payerId `@NotNull`, memo 길이 제한)
  - [ ] Controller: `POST /api/rounds/{roundId}/expenses`, `PUT/DELETE /api/expenses/{id}`, `PUT /api/expenses/{id}/shares`, `GET /api/meetings/{id}/expenses`
  - [ ] 에러 코드: `EXPENSE_NOT_FOUND`(404)
- **테스트**
  - [ ] `@WebMvcTest`: 금액 0·음수·소수(`1.5`)·100,000,001 → 400, 1·100,000,000 → 성공
  - [ ] 단위: 생성·수정·삭제 시 `ChangeLog` 1건(전/후 JSON), 정산 `CONFIRMED`면 `REOPENED`
  - [ ] 단위: 비멤버 403 `NOT_GROUP_MEMBER`
- **수락 기준**
  - Given 2차 차수 / When 금액 100,000원·결제자 철수로 지출을 등록하면 / Then 지출이 저장된다. 금액이 0 이하이거나 정수가 아니거나 1억 원을 넘으면 400을 받는다. 정산이 `CONFIRMED`면 지출 추가·수정·삭제 모두 처리되고 같은 트랜잭션에서 `Settlement`가 `REOPENED`가 된다. (PRD S-1 중 벌칙 무관 부분)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 023: 정산 계산 순수 도메인(벌칙 제외)·송금 최소화   ⬜ 대기

- **관련 기능:** S-2, S-3, S-5, S-8
- **선행 Task:** Task 020
- **선행 조건:** ⚠️ PRD 오픈 이슈 8(결제자가 분배 대상에서 제외됐거나 결제자 몫이 초과분보다 작을 때 S-8 초과분 차감 대상) — 해당 경계 케이스는 결정 전까지 구현 보류, 나머지는 진행
- **구현 사항**
  - [ ] `settlement/calc/` 패키지 — JPA·Spring 의존 없음
  - [ ] 입력 모델 `SettlementInput`(지출 목록: 금액·결제자·분배 대상자(차수 참석자 기본)·조정), 출력 `SettlementResult`(멤버별 부담액·순잔액, 송금 안내 목록, 이후 벌칙·경고 확장 자리)
  - [ ] `ShareDistributor` — N빵: `1인 = ceil(금액 ÷ 인원)`을 100원 단위 올림, 초과분(`1인 × 인원 − 금액`)은 결제자 몫에서 차감, 제외자 0원. ⚠️ 가중치·고정 금액 조정 시 올림·초과분 처리 규칙 PRD 미정 — 결정 후 구현
  - [ ] `NetBalanceCalculator` — 결제자별 받을 돈 − 낼 돈, 합 0 검증
  - [ ] `TransferMinimizer` — 순잔액 → 송금 안내 목록(건수 ≤ 0 아닌 인원 − 1, 결정적 정렬)
  - [ ] 모든 연산 `long`, 곱셈 `Math.multiplyExact`
- **테스트**
  - [ ] S-2: 2차 참석 4명, 100,000원, 벌칙 없음 → 4명 25,000원, 불참자 없음
  - [ ] S-3: 4명 중 지수 제외 → 지수 0원, 3명 분배(100,000 ÷ 3 = 33,333.3… → 33,400원, 초과분 200원 결제자 차감 → 33,200원, 합 100,000원)
  - [ ] S-8 경계: 나누어떨어짐(초과분 0), 1원 차이, 100원 미만 금액(예: 50원 ÷ 3), 1억 원
  - [ ] S-5: 예 1의 순잔액(철수 +76,800 / 민수 −30,000 / 영희 −23,400 / 지수 −23,400) → 3건, 합 76,800원, 송금 후 모두 0
  - [ ] S-5: 여러 채권자·채무자 혼합에서 건수 ≤ 0 아닌 인원 − 1, 결과 결정적
  - [ ] 순잔액 합 0 불변식 검증(여러 지출·여러 결제자)
- **수락 기준**
  - Given 2차 참석자 4명, 지출 100,000원, 벌칙 없음 / When 정산을 계산하면 / Then 4명 모두 25,000원이 배정되고 불참자는 분배 대상에 없다. (PRD S-2)
  - Given 참석자 4명 중 지수를 제외 / When 정산을 계산하면 / Then 지수 부담액은 0원이고 나머지 3명이 나눈다(S-8 적용). 가중치(비율) 조정 시 가중치에 비례해 배정된다. (PRD S-3)
  - Given 예 1의 순잔액 / When 송금 안내 목록을 생성하면 / Then 송금 건수 3건, 금액 합 76,800원인 목록이 나오고 모든 멤버의 순잔액이 송금 후 0이 된다. 송금 건수는 (순잔액이 0이 아닌 인원 − 1) 이하다. (PRD S-5)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 024: 정산 조회·최초 확정·리비전 1 스냅샷   ⬜ 대기

- **관련 기능:** S-5, S-10(최초 확정), S-11
- **선행 Task:** Task 022, Task 023
- **구현 사항**
  - [ ] 엔티티: `SettlementRevision`(settlement, revisionNo, confirmedBy, confirmedAt, warningsAcknowledged) — `(settlement, revisionNo)` 유니크, 수정 메서드 없음 / `RevisionMemberShare`(revision, member, shareAmount, netBalance) — `(revision, member)` 유니크 / `Transfer`(revision, from, to, amount) — 송금 상태 필드 없음(E1)
  - [ ] `SettlementQueryService.get(meetingId)` — `basisRevisionNo`: `CONFIRMED`·`REOPENED` = `currentRevisionNo` 기준 저장된 `Transfer`, `DRAFT` = null + 현재 계산값 "미리보기"(저장 안 함)
  - [ ] `SettlementConfirmService.confirm(meetingId, hostId, acknowledgeWarnings)` — 호스트만(403 `NOT_MEETING_HOST`), `lockForAmountChange`, `DRAFT → CONFIRMED`, 리비전 1 + 멤버별 스냅샷 + `Transfer`, `firstConfirmedAt`·`currentRevisionNo` 기록. `CONFIRMED`면 409 `SETTLEMENT_ALREADY_CONFIRMED`. `REOPENED` 재확정은 Task 039
  - [ ] 정산 입력 조립기 `SettlementInputAssembler`(DB → `SettlementInput`) — 이후 벌칙 입력 확장 자리
  - [ ] Controller: `GET /api/meetings/{id}/settlement`, `POST /api/meetings/{id}/settlement/confirm`
- **테스트**
  - [ ] 단위: 확정 시 리비전 1·`RevisionMemberShare`·`Transfer`가 계산값대로 저장, `firstConfirmedAt` 기록
  - [ ] 단위: `CONFIRMED` 재요청 409 `SETTLEMENT_ALREADY_CONFIRMED`, 비호스트 403
  - [ ] `@DataJpaTest`: `(settlement, revisionNo)`·`(revision, member)` 유니크
  - [ ] `@WebMvcTest`: `DRAFT` 조회 시 `basisRevisionNo = null`·미리보기 표시, `CONFIRMED` 조회 시 `basisRevisionNo = 1`
- **수락 기준**
  - Given `DRAFT` 정산 / When 호스트가 확정하면 / Then `Settlement`가 `CONFIRMED`가 되고 `SettlementRevision`(리비전 1, 확정자·시각, 멤버별 부담액·순잔액)과 그 리비전의 송금 안내 목록(`Transfer`: from·to·amount)이 그 시점 계산값으로 저장된다. `CONFIRMED` 상태에서 다시 요청하면 409(`SETTLEMENT_ALREADY_CONFIRMED`). (PRD S-10 중 벌칙 무관 부분)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 025: 확정 후 금액 영향 변경의 `REOPENED` 연결(참석 포함)   ⬜ 대기

- **관련 기능:** S-11, M-3, 6장 정산 직렬화·재시도
- **선행 Task:** Task 013, Task 024
- **선행 조건:** PRD 오픈 이슈 17(현재 안 유지 확인)
- **구현 사항**
  - [ ] `AttendanceService.respond`: 지출 1건 이상인 차수의 참석 변경이면 `lockForAmountChange` + `markAmountChanged`, 지출 없는 차수는 직렬화 제외
  - [ ] 참석 변경 `ChangeLog`는 정산이 한 번이라도 확정된 뒤(`currentRevisionNo ≥ 1`)부터만 기록
  - [ ] 참석 응답 Controller → `OptimisticRetryExecutor` facade → Service(재시도 시 같은 응답이 이미 저장됐으면 쓰기 없이 성공, 멱등)
  - [ ] `ExpenseShare`·지출 변경 경로가 모두 `markAmountChanged`를 거치는지 점검
  - [ ] "재확정 필요" 이벤트 페이로드: `ChangeLog` 요약(무엇이 바뀌었는지)
- **테스트**
  - [ ] 단위: 지출 있는 차수 참석 변경 + `CONFIRMED` → `REOPENED` + `ChangeLog` 1건
  - [ ] 단위: 지출 없는 차수 참석 변경 → 정산 상태·버전 변화 없음
  - [ ] 단위: 확정 전 참석 변경 → `ChangeLog` 없음
  - [ ] 통합(DB): 리비전 1 확정 → 지출 금액 수정 → `REOPENED`, `ChangeLog`(행위자, 시각, `Expense`, `UPDATE`, 전/후 JSON), 커밋 후 "정산 재확정 필요" 이벤트 1회
- **수락 기준**
  - Given 리비전 1로 확정된 정산 / When 멤버가 지출 금액을 수정하면 / Then 수정은 성공하고 같은 트랜잭션에서 `Settlement`가 `REOPENED`, `ChangeLog`에 (행위자, 시각, `Expense`, `UPDATE`, 전/후 JSON)이 저장되며, 커밋 후 모임 구독자에게 "정산 재확정 필요" 이벤트가 브로드캐스트된다. (PRD S-11 앞부분)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 6: 게임 방·사다리·꽝뽑기

- **목표:** 서버가 관리하는 게임 상태 머신(`WAITING → PLAYING → REVEALED → CLOSED`, `→ CANCELED`), 각자 폰 게임(사다리·꽝뽑기), 공정성(공개 전 결과 비노출), 이탈 처리
- **포함 기능 ID:** R-0, R-1, R-2, R-4(벌칙 종류·안내 문구), R-8, R-10

### Task 026: 게임 도메인 골격과 상태 전이 규칙   ⬜ 대기

- **관련 기능:** R-0, R-1, R-2, R-10, 4.4 게임 상태 전이 표
- **선행 Task:** Task 013, Task 021
- **구현 사항**
  - [ ] `game/domain/GameSession`(round, type `LADDER/DRAW/ROULETTE/BOWLING`, status, createdBy, penaltyKind `MONEY/ACTION/NONE`, penaltyNote ≤100자, seed·결과 배열(응답 DTO에 매핑 안 함), revealedAt?, applyOrderAt? `DATETIME(6)`, rootSessionId, rerunOf?(유니크, NULL 허용), canceledRerunOf?, timeLimit?, `@Version version`)
  - [ ] `GameParticipant`(gameSession, member, ready, pickedSlot?) — `(gameSession, member)` 유니크, `(gameSession, pickedSlot)` 유니크
  - [ ] `GameResult`(gameSession, member, outcome, rank/score?)
  - [ ] 전이 메서드(현재 상태 위반 시 409 `GAME_INVALID_TRANSITION`): `start()`, `reveal(now)`(revealedAt, 첫 공개면 applyOrderAt), `close()`, `cancel()`(WAITING/PLAYING만, 아니면 409 `GAME_NOT_CANCELABLE`)
  - [ ] `isOperator(memberId, meeting)` = `createdBy` 또는 호스트
  - [ ] `GameSessionRepository.findByIdForPick` — `@Lock(PESSIMISTIC_WRITE)`
  - [ ] 에러 코드: `GAME_INVALID_TRANSITION`, `GAME_NOT_READY`, `GAME_NOT_CANCELABLE`, `SLOT_ALREADY_PICKED`(모두 409), `NOT_GAME_OPERATOR`(403), `GAME_NOT_FOUND`(404)
- **테스트**
  - [ ] 단위(전이 표 전체): 허용 전이 5종 성공, 비허용 전이(예: REVEALED가 아닌데 close, WAITING이 아닌데 start, PLAYING이 아닌데 reveal) → `GAME_INVALID_TRANSITION`, REVEALED/CLOSED cancel → `GAME_NOT_CANCELABLE`, CLOSED·CANCELED에서 어떤 전이도 불가
  - [ ] 단위: 첫 공개만 applyOrderAt 기록(마이크로초 정밀도 유지)
  - [ ] `@DataJpaTest`: `rerunOf` 유니크(NULL 여러 건 허용), `(gameSession, pickedSlot)` 유니크, `applyOrderAt` 마이크로초 저장·조회
- **수락 기준**
  - Given `WAITING`이 아닌 게임 / When 시작하면 / Then 409(`GAME_INVALID_TRANSITION`)이고 상태는 그대로다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 027: 게임 방 생성·입장·준비·시작·종료·취소 API   ⬜ 대기

- **관련 기능:** R-0, R-4(벌칙 종류·안내 문구), R-10
- **선행 Task:** Task 026
- **구현 사항**
  - [ ] `GameLobbyService.create(roundId, type, penaltyKind, penaltyNote, 게임별 설정)` — 그룹 멤버 누구나, `createdBy` = 요청자, `rootSessionId` = 자기 id, `PenaltyRule`·`Penalty` 생성 없음. ⚠️ 참가자 결정 방식(차수 참석자 자동 로드 vs 로비 입장자) PRD에서 R-0("각자 입장")과 R-1("참가자 자동 로드")이 갈림
  - [ ] `join`, `ready`, `start`(진행자·호스트, 미준비 참가자 있으면 409 `GAME_NOT_READY`, 결과 생성은 Task 028·029 전략에 위임), `close`(진행자·호스트, `REVEALED → CLOSED`, 금액 영향 없음), `cancel`(진행자·호스트, 금액형이면 `SettlementGuard.lockOnly`)
  - [ ] ⚠️ 게임 취소 `ChangeLog` 기록 범위(전 종류 vs 금액형만) — PRD 7장은 "게임 취소" 기록, 6장은 금액 비영향. 결정 후 구현
  - [ ] 상태 변경마다 커밋 후 게임 토픽 브로드캐스트
  - [ ] DTO: `GameCreateRequest`(penaltyNote `@Size(max = 100)`), `GameStateResponse`(공개 전 결과 필드 없음)
  - [ ] Controller: `POST /api/rounds/{roundId}/games`, `POST /api/games/{id}/join|ready|start|close|cancel`
- **테스트**
  - [ ] 단위: 진행자·호스트 시작 성공, 그 외 멤버 403 `NOT_GAME_OPERATOR`, 미준비 409 `GAME_NOT_READY`
  - [ ] 단위: 종료 후 정산 상태·버전 변화 없음, 그 외 멤버 403, `REVEALED` 아님 409
  - [ ] 단위: 취소 — `CANCELED` + 브로드캐스트, `Penalty` 없음, `REVEALED` 409 `GAME_NOT_CANCELABLE`, 금액형 취소는 정산 버전 증가하되 `REOPENED` 아님
  - [ ] 단위(R-4): 금액형·안내 문구 저장, `PenaltyRule`·`Penalty` 0건, 안내 문구 101자 400
  - [ ] `@WebMvcTest`: 응답 형식, 안내 문구가 공개 전 응답에 포함
- **수락 기준**
  - Given 진행자가 만든 `WAITING` 게임 방 / When 참가자 전원이 준비를 누르면 / Then 진행자 또는 호스트가 시작할 수 있고 상태가 `PLAYING`으로 바뀌어 모든 참가자에게 브로드캐스트된다. 그 외 멤버가 시작하면 403(`NOT_GAME_OPERATOR`), 준비 안 한 참가자가 있으면 409(`GAME_NOT_READY`), `WAITING`이 아니면 409(`GAME_INVALID_TRANSITION`)이고 상태는 그대로다. Given `REVEALED` 게임 / When 진행자 또는 호스트가 "게임 종료"를 누르면 / Then `CLOSED`가 되어 브로드캐스트되고 `Penalty`·`PenaltyRule`·정산 상태는 바뀌지 않는다. 그 외 멤버면 403(`NOT_GAME_OPERATOR`), `REVEALED`가 아니면 409(`GAME_INVALID_TRANSITION`). (PRD R-0)
  - Given 게임 생성 화면 / When 벌칙 종류 "금액형"과 안내 문구 "꼴찌가 2차 30%"를 입력하면 / Then `GameSession.penaltyKind = MONEY`, `penaltyNote`가 저장되고 `PenaltyRule`·`Penalty`는 생성되지 않는다. 안내 문구는 선택 입력(최대 100자)이며 정산 계산에 쓰이지 않는다. (PRD R-4 앞부분)
  - Given 진행자가 만든 `WAITING`/`PLAYING` 게임 / When 진행자 또는 호스트가 취소하면 / Then 상태가 `CANCELED`가 되어 브로드캐스트되고 `Penalty`는 생성되지 않는다. 그 외 멤버가 취소하면 403(`NOT_GAME_OPERATOR`), `REVEALED`/`CLOSED` 게임이면 409(`GAME_NOT_CANCELABLE`). 취소와 결과 공개가 동시에 오면 `GameSession.version`으로 하나만 성공한다. (PRD R-10 앞부분)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 028: 사다리 타기   ⬜ 대기

- **관련 기능:** R-1
- **선행 Task:** Task 027
- **구현 사항**
  - [ ] `game/ladder/LadderGenerator`(순수, `SecureRandom` 주입 가능) — 시작 시 연결 생성·저장
  - [ ] 결과 항목 입력(꽝/벌칙/당첨) — ⚠️ 항목 수 = 참가자 수인지, 어떤 결과(꽝·벌칙)가 벌칙자가 되는지 PRD 미정(Task 032에 영향)
  - [ ] `reveal`(진행자·호스트) → `REVEALED`, 공개 응답·브로드캐스트에 동일 연결 데이터
  - [ ] 공개 전 응답·메시지에서 연결 정보 제외(DTO 수준에서 필드 자체 없음)
  - [ ] 공개 요청은 `OptimisticRetryExecutor` facade 경유(이미 `REVEALED`면 멱등 성공)
- **테스트**
  - [ ] 단위(`LadderGenerator`): 참가자 4·항목 4 → 전단사 매핑, 고정 시드 재현성
  - [ ] `@WebMvcTest`/직렬화 테스트: `PLAYING` 상태 응답 JSON에 연결 필드 없음
  - [ ] 단위: 비운영자 공개 403 `NOT_GAME_OPERATOR`, `PLAYING` 아님 409 `GAME_INVALID_TRANSITION`
- **수락 기준**
  - Given 참가자 4명·결과 항목 4개 / When 게임이 시작되면 / Then 서버가 사다리 연결을 생성·저장하고, `REVEALED` 전까지 어떤 클라이언트 응답·WebSocket 메시지에도 연결 정보가 포함되지 않는다. Given `PLAYING` 사다리 / When 진행자 또는 호스트가 결과 공개를 요청하면 / Then `REVEALED`가 되고 모든 참가자가 같은 연결 데이터를 받는다. 그 외 멤버면 403(`NOT_GAME_OPERATOR`), `PLAYING`이 아니면 409(`GAME_INVALID_TRANSITION`). (PRD R-1)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 029: 꽝뽑기 카드 선택·동시 공개   ⬜ 대기

- **관련 기능:** R-2
- **선행 Task:** Task 027
- **구현 사항**
  - [ ] `game/draw/DrawDeckGenerator`(순수) — N장 중 꽝 K장을 시작 시 섞어 저장. ⚠️ N과 참가자 수의 관계(N = 참가자 수 강제 여부) PRD 미정
  - [ ] `DrawPickService.pick(gameId, memberId, slot)` — `findByIdForPick`(`PESSIMISTIC_WRITE`) → `PLAYING`·참가자 본인·미선택 검사 → 슬롯 점유 검사 → 저장(`saveAndFlush`), `(gameSession, pickedSlot)` 유니크 위반은 409 `SLOT_ALREADY_PICKED`, **재시도 없음**
  - [ ] 전원 선택 시 같은 트랜잭션에서 `REVEALED` + 결과 일괄 공개(커밋 후 브로드캐스트)
  - [ ] 공개 전 응답에 카드 배열 없음(본인 선택 슬롯 번호만)
- **테스트**
  - [ ] 단위(`DrawDeckGenerator`): N=5, K=1 → 꽝 정확히 1장
  - [ ] 통합(DB, 동시성): 두 참가자가 같은 카드를 동시에 선택 → 1명 성공, 1명 409 `SLOT_ALREADY_PICKED`
  - [ ] 통합(DB, 동시성): 서로 다른 카드 동시 선택 → 둘 다 성공(낙관적 락 실패 없음)
  - [ ] 단위: 마지막 선택 시 `REVEALED`, 이미 선택한 참가자 재선택 거부
- **수락 기준**
  - Given N=5, K=1 꽝뽑기 / When 두 참가자가 같은 카드를 동시에 고르면 / Then `GameSession` 행 잠금(`PESSIMISTIC_WRITE`) 순서대로 처리되어 먼저 도착한 요청만 성공하고 다른 쪽은 409(`SLOT_ALREADY_PICKED`)를 받는다(재시도 없음). 전원이 뽑으면 상태가 `REVEALED`가 되고 결과가 동시에 공개된다. (PRD R-2)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 030: 이탈 처리·재접속 상태 복구   ⬜ 대기

- **관련 기능:** R-8
- **선행 Task:** Task 028, Task 029
- **구현 사항**
  - [ ] `DrawTimeoutScheduler`(`@Scheduled`) — 제한 시간 지난 `PLAYING` 꽝뽑기 → `PESSIMISTIC_WRITE` 획득 후 아직 `PLAYING`이고 미선택자가 있을 때만 남은 카드 자동 배정·공개, 이미 `REVEALED`면 쓰기 없이 종료(멱등). ⚠️ 제한 시간 기본값·설정 주체 PRD 미정
  - [ ] `GET /api/games/{id}/state` — 현재 상태 + 본인 선택(공개 전 결과 제외), 공개 후엔 전체 결과
  - [ ] 자동 배정 순서 결정적(예: 미선택자 memberId 순 × 남은 슬롯 순)
- **테스트**
  - [ ] 단위: 제한 시간 경과 → 남은 카드 자동 배정·`REVEALED`(고정 `Clock`)
  - [ ] 통합(DB, 동시성): 마지막 수동 선택과 자동 배정이 동시에 → 이중 배정·이중 공개 없음
  - [ ] `@WebMvcTest`: 재접속 상태 조회 — 공개 전 응답에 다른 사람 카드·꽝 위치 없음, 본인 선택 포함
- **수락 기준**
  - Given 제한 시간이 있는 꽝뽑기 / When 제한 시간이 지나도 안 뽑은 참가자가 있으면 / Then 서버가 남은 카드를 자동 배정하고 공개한다. Given 연결이 끊긴 참가자 / When 재접속하면 / Then REST로 현재 게임 상태(본인 선택 포함, 공개 전 결과 제외)를 받아 화면을 복구한다. (PRD R-8)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 7: 벌칙자 확정·다시 돌리기

- **목표:** 결과 공개 시 `Penalty`(`ASSIGNED`) 생성, 다시 돌리기(권한·복제·`rerunOf` 유니크), 새 회차 공개 시 `VOIDED`·규칙 이관·조건 상태 상속, 새 회차 취소
- **포함 기능 ID:** R-4, R-5, R-10

### Task 031: 벌칙·벌칙 규칙 도메인 골격   ⬜ 대기

- **관련 기능:** R-5, R-9, 4.5 벌칙 상태 전이 표
- **선행 Task:** Task 026
- **구현 사항**
  - [ ] `penalty/domain/Penalty`(gameSession, member, status `ASSIGNED/APPLIED/WAIVED/VOIDED`, waivedBy?, waivedAt?, `@Version`) — `(gameSession, member)` 유니크, `chargedAmount` 필드 없음
  - [ ] `penalty/domain/PenaltyRule`(gameSession 유니크, moneyType `FIXED_AMOUNT/RATIO/FULL`, amount?, ratioPct?, targetExpense 필수 FK, setBy, updatedAt, `@Version`) + `moveTo(newGameSession)`(setBy·updatedAt 유지)
  - [ ] `Penalty` 전이 메서드(4.5 표 전체): apply, waive, unwaive, unapply, void(종단), 허용 안 된 전이는 409 `PENALTY_NOT_CONFIGURABLE`
  - [ ] Repository: `findAllByGameSessionIdAndStatusNot(VOIDED)`, `PenaltyRuleRepository.findByGameSessionId`, `findAllByTargetExpenseId`
- **테스트**
  - [ ] 단위(전이 표 전체): `ASSIGNED→APPLIED/WAIVED`, `APPLIED→ASSIGNED/WAIVED`, `WAIVED→ASSIGNED/APPLIED`, 모든 비종단 → `VOIDED`, `VOIDED`에서 어떤 전이도 불가
  - [ ] 단위: `PenaltyRule.moveTo` 후 setBy·updatedAt 불변
  - [ ] `@DataJpaTest`: `(gameSession, member)` 유니크, `PenaltyRule.gameSession` 유니크
- **수락 기준**
  - Given `VOIDED` 벌칙 / When 어떤 전이를 시도하면 / Then 409로 거부된다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 032: 결과 공개 시 벌칙자 확정   ⬜ 대기

- **관련 기능:** R-5, R-4
- **선행 Task:** Task 028, Task 029, Task 031
- **구현 사항**
  - [ ] `PenaltyAssigner.onRevealed(gameSession)` — 공개 전이와 같은 트랜잭션. `penaltyKind = NONE`이면 생성 없음, `MONEY`·`ACTION`이면 벌칙자별 `ASSIGNED`를 **memberId 오름차순** insert
  - [ ] 금액형이면 `SettlementGuard.lockForAmountChange` + `markAmountChanged` + 최초 공개 `ChangeLog`
  - [ ] 공개 중복 실행 방어: `(gameSession, member)` 유니크 + `GameSession.version`
  - [ ] ⚠️ 사다리 결과 중 어떤 항목이 벌칙자가 되는지(Task 028 참고) 결정 후 반영
- **테스트**
  - [ ] 단위: 금액형 사다리 "꽝" 2명(민수·영희) → `Penalty` 2건 `ASSIGNED`, 금액 필드 없음, `revealedAt`·`applyOrderAt` 기록, `ChangeLog` 1건
  - [ ] 단위: `NONE` → `Penalty` 0건, `ACTION` → `ASSIGNED`지만 정산 상태·버전 변화 없음
  - [ ] 단위: insert 순서가 memberId 오름차순(입력 순서를 섞어도)
  - [ ] 통합(DB): `CONFIRMED` 정산에서 금액형 공개 → `REOPENED`
  - [ ] 통합(DB): 공개 처리 중복 실행 → 1인 1건
- **수락 기준**
  - Given `penaltyKind = MONEY` 사다리, 결과 "꽝" 2명(민수·영희) / When 결과가 공개되면 / Then 민수·영희 각각 `Penalty(gameSession, member)`가 `ASSIGNED`로 생성되고 금액은 계산·저장되지 않으며, `revealedAt`·`applyOrderAt`이 기록된다. 공개 처리가 중복 실행돼도 `(gameSession, member)` 유니크로 1인 1건이며, `Penalty`는 `memberId` 오름차순으로 생성된다. 금액형 게임의 최초 결과 공개는 `ChangeLog`에 기록된다. (PRD R-5 앞부분)
  - 벌칙 종류 "없음"으로 만든 게임은 결과가 공개돼도 `Penalty`가 생성되지 않는다. (PRD R-4)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 033: 다시 돌리기 요청과 다시 돌린 회차 취소   ⬜ 대기

- **관련 기능:** R-5, R-10, E3
- **선행 Task:** Task 032
- **구현 사항**
  - [ ] `GameRerunService.request(originalId, requesterId)` — 권한(원 회차 진행자 또는 호스트, 그 외 403 `NOT_GAME_OPERATOR`, 비멤버 403 `NOT_GROUP_MEMBER`) → 사전 검사(`REVEALED`/`CLOSED`이면서 후속 회차 없음, 아니면 409 `GAME_NOT_RERUNNABLE`) → 새 `GameSession`(`WAITING`, rerunOf, createdBy = 요청자, applyOrderAt·rootSessionId 상속, type·penaltyKind·penaltyNote·참가자 복제) `saveAndFlush`, 유니크 위반 → 409 `RERUN_ALREADY_EXISTS`
  - [ ] 요청 DTO에 type·penaltyKind·penaltyNote·참가자 필드 없음
  - [ ] 원 회차 행은 수정하지 않음(락 없음), 원 `Penalty`·`PenaltyRule` 그대로
  - [ ] 금액형이면 `lockForAmountChange` + `markAmountChanged` + `ChangeLog`
  - [ ] 취소 확장: 다시 돌린 회차 취소 시 `rerunOf` 비우고 `canceledRerunOf` = 원 회차
  - [ ] 게임 이력 조회에 재실행·취소 이력 공개
  - [ ] 에러 코드: `GAME_NOT_RERUNNABLE`, `RERUN_ALREADY_EXISTS`(409)
- **테스트**
  - [ ] 단위: 진행자·호스트 요청 성공, 필드 복제·상속 확인, 원 `Penalty` 불변
  - [ ] 단위: 그 외 멤버 403 `NOT_GAME_OPERATOR`, `WAITING`/`PLAYING`/`CANCELED`·후속 회차 있음 → 409 `GAME_NOT_RERUNNABLE`, 아무것도 생성 안 됨
  - [ ] 통합(DB, 동시성): 같은 회차 동시 2건 → 1건 성공, 늦은 쪽 409 `RERUN_ALREADY_EXISTS` 또는 `GAME_NOT_RERUNNABLE`
  - [ ] 통합(DB): `CONFIRMED` 정산 금액형 다시 돌리기 → 같은 트랜잭션 `REOPENED` + `ChangeLog`
  - [ ] 단위: 다시 돌린 회차(`WAITING`) 취소 → 원 회차 `Penalty`·`PenaltyRule` 불변, 원 회차 재요청 가능
- **수락 기준**
  - When 원 회차 진행자 또는 호스트가 "다시 돌리기"를 요청하면 / Then `rerunOf`가 원 회차를 가리키고 `applyOrderAt`을 이어받은 새 `GameSession`(`WAITING`, `createdBy` = 요청자, `type`·`penaltyKind`·`penaltyNote`·참가자 목록은 원 회차 복제)이 생기고, 원 회차 `Penalty`는 아직 바뀌지 않는다. 진행자·호스트가 아닌 그룹 멤버가 요청하면 403(`NOT_GAME_OPERATOR`), 원 회차가 `REVEALED`/`CLOSED`가 아니거나 이미 후속 회차가 있으면 409(`GAME_NOT_RERUNNABLE`)를 받고 아무것도 생성되지 않는다. 같은 회차에 다시 돌리기 요청 2건이 동시에 오면 1건만 성공하고, 늦은 쪽은 409 `RERUN_ALREADY_EXISTS` 또는 409 `GAME_NOT_RERUNNABLE` 중 하나를 받는다. Given 정산이 `CONFIRMED`인 모임의 금액형 게임 / When 다시 돌리기를 요청하면 / Then 새 회차가 생성되고 같은 트랜잭션에서 `Settlement`가 `REOPENED`가 되며 `ChangeLog`에 다시 돌리기가 기록된다. (PRD R-5)
  - Given 다시 돌린 회차(`WAITING`) / When 취소하면 / Then 원 회차 `Penalty`·`PenaltyRule`은 바뀌지 않고, 원 회차에 다시 돌리기를 새로 요청할 수 있다. (PRD R-10)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 034: 다시 돌린 회차 결과 공개 (`VOIDED`·규칙 이관·조건 상태 상속)   ⬜ 대기

- **관련 기능:** R-5, 4.4 다시 돌리기 처리 시점
- **선행 Task:** Task 033
- **구현 사항**
  - [ ] `PenaltyAssigner.onRevealed` 확장 — `rerunOf != null`이면 한 트랜잭션에서: 원 회차 비무효 `Penalty` → `VOIDED`, 원 회차 `PenaltyRule` → 새 회차로 `moveTo`(+ `ChangeLog` 이관 기록), 새 벌칙자 생성(원 회차 공개 직전 상태: 규칙 있음 → `APPLIED`, 면제 → `WAIVED`(waivedBy·waivedAt 상속), 미설정 → `ASSIGNED`), memberId 오름차순
  - [ ] 행동형은 `ASSIGNED → VOIDED`만, 정산 무관
  - [ ] 금액형이면 직렬화·`REOPENED`·`ChangeLog`
- **테스트**
  - [ ] 단위: 원 회차 `APPLIED` → 새 벌칙 `APPLIED`, 규칙 이관(setBy·updatedAt 불변), 원 벌칙 `VOIDED`(삭제 아님)
  - [ ] 단위: 원 회차 `WAIVED` → 새 벌칙 `WAIVED`(waivedBy·waivedAt 상속), 원 회차 `ASSIGNED` → 새 벌칙 `ASSIGNED`
  - [ ] 단위: 원 회차 벌칙자와 새 벌칙자가 겹쳐도 `(gameSession, member)` 위반 없음(회차가 다름)
  - [ ] 통합(DB): 이관 후 대상 지출 삭제 시 FK 문제 없음(Task 037과 함께 재검증)
- **수락 기준**
  - When 새 회차 결과가 공개되면 / Then 원 회차 `Penalty`는 삭제되지 않고 `VOIDED`(이력에만 표시, 정산 제외)가 되고, 원 회차 `PenaltyRule`은 새 회차로 이관되며, 새 벌칙자는 원 회차의 공개 직전 조건 상태대로 `APPLIED`/`WAIVED`/`ASSIGNED`로 생성된다. (PRD R-5)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 8: 벌칙 정산 연동·확정 게이트

- **목표:** 벌칙 부담액 파생 계산(정렬 키·잔액 상한·D3 균등 분할·경고 m4), R-9 조건 설정·면제(E2), 대상 지출 삭제 복귀, 벌칙 반영 내역, S-10 확정 게이트와 `PenaltyCharge` 스냅샷
- **포함 기능 ID:** R-9, S-1(대상 지출 삭제), S-4, S-8, S-10

### Task 035: 벌칙 부담액 계산·경고 순수 도메인   ⬜ 대기

- **관련 기능:** S-8, R-9, 4.5 벌칙 정산 규칙·정산 계산 순서
- **선행 Task:** Task 023, Task 031
- **선행 조건:** PRD 오픈 이슈 2(벌칙자를 나머지 분배에서 뺄지 — 현재 안 "빠짐", PRD 계산 예시·수용 기준 전부 현재 안 기준). 착수 전 현재 안 유지 확인
- **구현 사항**
  - [ ] `settlement/calc/PenaltyChargeCalculator` — 입력: 지출별 규칙 목록(moneyType, 값, 정렬 키 `(applyOrderAt, rootSessionId)`, 벌칙자 목록(penaltyId, memberId), 결제자)
    - [ ] 정렬 키 순으로 규칙 단위 잔액 차감
    - [ ] 1인 계산값 v: `FIXED_AMOUNT` = 금액, `RATIO` = `(대상 금액 × pct + 9,999) / 10,000 × 100`, `FULL` = 그 시점 잔액(올림 없음)
    - [ ] D3: `k × v ≤ R`이면 각자 v, 아니면 1인 = `(R + 100k − 1) / (100k) × 100`, 초과분 E를 ① 벌칙자 중 결제자 → ② `Penalty.id` 내림차순으로 0원 밑으로 안 가게 차감, 합계 정확히 R
  - [ ] `PenaltyWarningEvaluator` — 지출별 `Σ_규칙 min(k × v₁₀₀, max(v₁₀₀, T₁₀₀)) > T₁₀₀`(×100 스케일, `Math.multiplyExact`, v_raw는 올림 전 값)
  - [ ] `SettlementCalculator` 확장: 1) 지출별 금액 → 2) `APPLIED` 벌칙 차감(`ASSIGNED`·`WAIVED`·`VOIDED` 제외) → 3) 남은 금액을 그 지출의 `APPLIED` 벌칙자 제외 분배 대상자에게 기본 분배(S-8) → 4) 순잔액 → 5) 송금 최소화. 결과에 벌칙별 부담액·경고 목록 포함
- **테스트** (금액은 모두 원 단위 정수, 100원 단위 올림 규칙 명시 검증)
  - [ ] 예 1(`RATIO 30%`, 100,000원): 민수 30,000 / 철수 23,200 / 영희 23,400 / 지수 23,400, 합 100,000, 순잔액 철수 +76,800, 송금 3건 합 76,800, 경고 없음
  - [ ] 예 2(`FIXED_AMOUNT 20,000`): 철수 26,600 / 영희·지수 26,700, 합 100,000
  - [ ] 예 3(`FIXED_AMOUNT 150,000`): 민수 100,000, 나머지 0, 송금 민수→철수 100,000, **경고 발생**
  - [ ] 예 4-a(`FULL`, 87,650원): 민수 87,650, 나머지 0, 경고 없음
  - [ ] 예 4-b(`RATIO 50%` × 다른 게임 2개, 민수 먼저 공개): 민수 43,900 / 영희 43,750, 합 87,650, 경고 없음
  - [ ] 예 5-a(한 규칙 `RATIO 30%`, 민수·영희): 각 26,300, 철수 17,450 / 지수 17,600, 순잔액 철수 +70,200, 경고 없음
  - [ ] 예 5-b(지수 `FIXED 50,000` 먼저 + 민수·영희 `RATIO 30%`): 지수 50,000 / 민수 18,900 / 영희 18,750 / 철수 0, 합 87,650, **경고 발생**(10,259,000 > 8,765,000)
  - [ ] S-8 수용 기준 `FULL` 2명(민수·영희, 87,650원): 민수 43,900 / 영희 43,750, **경고 없음**(m4)
  - [ ] D3 초과분 차감 ①: 결제자가 벌칙자 중에 있으면 결제자 몫에서 먼저
  - [ ] 정렬 키: `applyOrderAt` 같으면 `rootSessionId` 오름차순, 다시 돌린 회차는 원 회차 순번 유지
  - [ ] `RATIO` 경계: pct 1·100, 1원 지출, 1억 지출(`multiplyExact` 오버플로 없음)
  - [ ] 벌칙자가 `APPLIED`가 아닌(`ASSIGNED`·`WAIVED`) 멤버는 일반 참석자로 분배
- **수락 기준**
  - Given 예 1 조건 / When 정산을 계산하면 / Then 영희·지수 23,400원, 철수 23,200원, 민수 30,000원이고 부담액 합이 지출 합계 100,000원과 정확히 같다. Given 예 4-b 조건 / Then 민수 43,900원, 영희 43,750원이고 벌칙 합계가 87,650원을 넘지 않으며 경고가 뜨지 않는다. Given 예 5-b 조건 / Then 지수 50,000원, 민수 18,900원, 영희 18,750원, 철수 0원이고 벌칙 합계가 정확히 87,650원이며 경고가 뜬다. Given 예 3 조건 / Then 민수 100,000원이고 경고가 뜬다. Given 예 4 조건에서 한 게임 `FULL` 규칙, 벌칙자 민수·영희 / Then 민수 43,900원·영희 43,750원이고 경고가 뜨지 않는다. (PRD S-8)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 036: 벌칙 조건 설정·변경·해제·면제·면제 취소 API   ⬜ 대기

- **관련 기능:** R-9, E2, R-4(프리셋)
- **선행 Task:** Task 022, Task 034
- **구현 사항**
  - [ ] `PenaltyConditionService` (모두 호스트만, 403 `NOT_MEETING_HOST`)
    - [ ] `setRule(gameId, moneyType, value, targetExpenseId)` — 검증: 게임 `MONEY`·`REVEALED`/`CLOSED`(아니면 409 `PENALTY_NOT_CONFIGURABLE`), 대상 지출 존재(404 `EXPENSE_NOT_FOUND`)·같은 모임(400 `EXPENSE_NOT_IN_MEETING`), 비무효 벌칙만 대상. `ASSIGNED` → 규칙 생성 + 전원 `APPLIED`, `APPLIED` → 규칙 갱신(전이 없음), `WAIVED` → 면제 취소 + 규칙 생성 + 전원 `APPLIED`(`ChangeLog` 2건)
    - [ ] `clearRule` — 규칙 삭제 + 전원 `ASSIGNED`
    - [ ] `waive` — `ASSIGNED` → 전원 `WAIVED`, `APPLIED` → 규칙 삭제 + 전원 `WAIVED`(`ChangeLog` 2건), waivedBy·waivedAt 기록
    - [ ] `unwaive` — `WAIVED` → 전원 `ASSIGNED`, waivedBy·waivedAt 비움
    - [ ] 모든 변경: `lockForAmountChange` + `markAmountChanged` + `ChangeLog`(전/후 값)
  - [ ] DTO `PenaltyRuleRequest`: moneyType `@NotNull`, targetExpenseId `@NotNull`, `RATIO`면 ratioPct 1~100 정수, `FIXED_AMOUNT`면 amount 1~1억, `FULL`이면 값 없음(클래스 수준 검증)
  - [ ] 동시성: `Penalty.version`·`PenaltyRule.version` 낙관적 락, 늦은 쪽 409
  - [ ] Controller: `PUT/DELETE /api/games/{id}/penalty-rule`, `POST/DELETE /api/games/{id}/penalty-waiver`
- **테스트**
  - [ ] 단위(예 5-a 연계): 민수·영희 `ASSIGNED`, 볼링 87,650원 → `RATIO 30%` 설정 → 규칙 1건·두 벌칙 `APPLIED`, 계산 시 각 26,300원
  - [ ] `@WebMvcTest`: targetExpense 누락 400, pct 0·101·소수 400, 고정 금액 0·100,000,001 400
  - [ ] 단위: 없는 지출 404 `EXPENSE_NOT_FOUND`, 다른 모임 지출 400 `EXPENSE_NOT_IN_MEETING`, 비호스트 403 `NOT_MEETING_HOST`, `NONE`·`ACTION` 게임·`WAITING`/`PLAYING`/`CANCELED` 409, `VOIDED` 벌칙 409
  - [ ] 단위: `CLOSED` 게임도 `REVEALED`와 동일하게 처리
  - [ ] 단위(E2): `APPLIED → WAIVED` 한 트랜잭션 규칙 삭제 + 전원 `WAIVED` + `ChangeLog` 2건, `WAIVED → APPLIED` 면제 취소 + 규칙 생성 + `ChangeLog` 2건
  - [ ] 통합(DB): `CONFIRMED`에서 조건 변경 → `REOPENED`, 동시 조건 설정 2건 → 1건 409
- **수락 기준**
  - PRD R-9 수용 기준 전체(조건 설정 → 각자 26,300원, 400/404/403/409 케이스, `CLOSED` 동일 처리, "금액 벌칙 없음" → 전원 `WAIVED`, `APPLIED → WAIVED` `ChangeLog` 2건, `WAIVED → APPLIED` `ChangeLog` 2건, 모든 변경 `ChangeLog` 전/후 값, `CONFIRMED`면 같은 트랜잭션 `REOPENED`)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 037: 대상 지출 삭제 연동·벌칙 반영 내역 표시   ⬜ 대기

- **관련 기능:** S-1, S-4
- **선행 Task:** Task 035, Task 036
- **구현 사항**
  - [ ] `ExpenseService.delete` 확장 — `targetExpense`로 지정한 `PenaltyRule` 삭제(삭제 전 값 `ChangeLog`) + 해당 게임 `APPLIED` → `ASSIGNED`, 같은 트랜잭션, 응답·이벤트에 "대상 지출이 삭제되어 조건을 다시 입력해야 함"
  - [ ] `SettlementInputAssembler` 확장 — 비무효 `APPLIED` 벌칙·규칙·정렬 키 조립
  - [ ] 정산 조회 응답에 벌칙 반영 내역: `APPLIED` = 게임 출처·대상 지출·금액·설정자·설정 시각("민수: 사다리 꽝 → 2차 볼링 +30,000원"), `ASSIGNED` = "조건 미설정", `WAIVED` = "금액 벌칙 없음" + waivedBy·waivedAt, `VOIDED` = 이력 조회에서만
  - [ ] 정산 조회 응답에 경고 목록 포함
- **테스트**
  - [ ] 단위: 대상 지출 삭제 → 규칙 삭제 + `ASSIGNED` + `ChangeLog`, `CONFIRMED`면 `REOPENED`
  - [ ] 단위: 대상 지출 금액 수정 → 벌칙 상태 그대로, 다음 계산에서 새 금액 기준 부담액
  - [ ] `@WebMvcTest`/단위: 벌칙 상태별 표시 항목, `VOIDED`는 정산 탭 응답에 없음
  - [ ] 통합(DB): 다시 돌려 이관된 규칙의 대상 지출 삭제 시 FK 오류 없음
- **수락 기준**
  - Given `APPLIED` 벌칙의 `targetExpense`인 지출 / When 그 지출을 삭제하면 / Then 같은 트랜잭션에서 `PenaltyRule`이 삭제(`ChangeLog`에 삭제 전 값 기록)되고 해당 게임 벌칙이 `ASSIGNED`로 돌아간다. (PRD S-1)
  - Given 민수에게 2차 지출 "볼링" 대상 `RATIO 30%` 벌칙이 `APPLIED` / When 정산 탭을 조회하면 / Then "민수: 사다리 꽝 → 2차 볼링 +30,000원" 형식으로 벌칙 출처(게임)·대상 지출·금액·조건 설정자·설정 시각이 항목별로 노출된다. `ASSIGNED` 벌칙은 금액 없이 "조건 미설정", `WAIVED`는 "금액 벌칙 없음"과 면제자·면제 시각으로 표시되고, `VOIDED`는 이력에서만 보인다. (PRD S-4)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 038: 정산 확정 게이트·`PenaltyCharge` 스냅샷   ⬜ 대기

- **관련 기능:** S-10
- **선행 Task:** Task 024, Task 037
- **구현 사항**
  - [ ] `SettlementConfirmGate.check(meetingId, acknowledgeWarnings)` — 순서대로 ① 금액형 `ASSIGNED` 있음 → 409 `PENALTY_CONDITION_UNSET` + 미설정 벌칙 목록(행동형 제외) ② 금액형 `WAITING`/`PLAYING` 게임(다시 돌린 회차 포함) → 409 `MONEY_GAME_IN_PROGRESS` + 게임 목록 ③ 경고 있음 + 미확인 → 409 `WARNING_UNCONFIRMED` + 경고 내용
  - [ ] 에러 응답에 목록 데이터를 싣는 방식(`BusinessException` 확장 또는 상세 payload) 정리
  - [ ] `PenaltyCharge`(revision, penalty, amount, moneyType, fixedAmount?, ratioPct?, targetExpenseId(값 컬럼, FK 아님), targetExpenseAmount, targetExpenseMemo?) — `(revision, penalty)` 유니크
  - [ ] `SettlementConfirmService.confirm`에 게이트 연결 + `APPLIED` 벌칙별 `PenaltyCharge` 스냅샷 + `warningsAcknowledged` 기록
- **테스트**
  - [ ] 단위: 금액형 `ASSIGNED` 1건 → 409 `PENALTY_CONDITION_UNSET` + 목록, 정산 상태 불변 / 조건 입력 또는 면제 후 재요청 → 확정
  - [ ] 단위: 행동형 `ASSIGNED`만 있으면 차단 안 됨
  - [ ] 단위: 다시 돌린 금액형 회차 `WAITING` → 409 `MONEY_GAME_IN_PROGRESS`, 취소 후 확정 가능
  - [ ] 단위: 예 3·예 5-b 조건에서 `acknowledgeWarnings` 없이 → 409 `WARNING_UNCONFIRMED`, `true`로 재요청 → 확정
  - [ ] `@DataJpaTest`: `PenaltyCharge` 스냅샷 컬럼 저장, 대상 지출 삭제 후에도 스냅샷 유지(FK 아님)
  - [ ] 통합(DB): 확정 검사 직후 금액 영향 쓰기가 끼어들면 한쪽 409(직렬화)
- **수락 기준**
  - PRD S-10 수용 기준 중 리비전 1까지(미설정 409 `PENALTY_CONDITION_UNSET` + 목록, 진행 중 금액형 게임 409 `MONEY_GAME_IN_PROGRESS`, 경고 409 `WARNING_UNCONFIRMED` → `acknowledgeWarnings=true`로 확정, 비호스트 403, 확정 시 `SettlementRevision`(리비전 1, 멤버별 부담액·순잔액, `APPLIED` 벌칙별 `PenaltyCharge`)과 `Transfer` 저장)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 9: 재확정·리비전 비교·알림 (PRD 5-b)

- **목표:** 리비전이 2개 이상이어야 의미 있는 것 — 재확정(리비전 +1, 게이트 재사용), 새 리비전 전체 송금 안내, 1인당 변동액, 이력 조회, 재확정 알림·공유 문구
- **포함 기능 ID:** S-10(재확정), S-11

### Task 039: 재확정과 1인당 변동액   ⬜ 대기

- **관련 기능:** S-10, S-11, E1
- **선행 Task:** Task 038
- **구현 사항**
  - [ ] `SettlementConfirmService.confirm` 확장 — `REOPENED`면 같은 게이트 → 리비전 `currentRevisionNo + 1` 스냅샷(`RevisionMemberShare`·`PenaltyCharge`·전체 `Transfer`), 이전 리비전은 불변
  - [ ] `settlement/calc/NetBalanceDeltaCalculator`(순수) — 입력 순잔액 맵 2개(직전 리비전, 새 리비전) → 멤버별 `Δ = N_new − N_prev`(한쪽에 없으면 0), `ΣΔ = 0` 검증. 저장하지 않음
  - [ ] 재확정 응답에 변동액 목록 + 고정 안내 문구("이전 정산대로 이미 송금했다면 변동액만 앱 밖에서 더 보내거나 돌려받으세요. 아직 안 보냈다면 새 송금 안내대로 보내면 됩니다.")
- **테스트**
  - [ ] 단위(`NetBalanceDeltaCalculator`): 예 6 — 철수 +6,600 / 민수 −20,000 / 영희 +6,700 / 지수 +6,700, 합 0
  - [ ] 단위: 한쪽 리비전에만 있는 멤버 → 0 기준, 결과 같은 재확정 → 전원 0원
  - [ ] 단위(계산기 연계): 예 6 리비전 2 — 민수 50,000 / 철수 16,600 / 영희·지수 16,700, 순잔액 철수 +83,400, 송금 3건(민수→철수 50,000, 영희→철수 16,700, 지수→철수 16,700, 합 83,400)
  - [ ] 통합(DB): 리비전 1 확정 → 조건 `RATIO 50%` 변경(`REOPENED`) → 재확정 → 리비전 2 저장, 리비전 1 `Transfer` 3건 그대로
  - [ ] 단위: `REOPENED` 재확정에도 미설정·진행 중 게임·경고 게이트 적용
- **수락 기준**
  - Given `REOPENED` 정산 / When 호스트가 재확정하면 / Then 같은 검사(미설정·진행 중 금액형 게임·경고 확인)를 다시 거쳐 리비전 +1이 저장되고, 송금 안내는 새 순잔액 전체 기준으로 생성되며 이전 리비전 `Transfer`는 바뀌지 않는다(예 6). (PRD S-10)
  - Given 예 6 조건 / When 재확정하면 / Then 리비전 2 송금 안내는 민수→철수 50,000, 영희→철수 16,700, 지수→철수 16,700(3건, 합 83,400원)이고, 리비전 1 대비 1인당 변동액은 철수 +6,600 / 민수 −20,000 / 영희 +6,700 / 지수 +6,700(합 0)으로 반환되며, 리비전 1의 송금 안내 3건은 그대로 남는다. (PRD S-11)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 040: 리비전·변경 로그 조회와 재확정 알림·공유 문구   ⬜ 대기

- **관련 기능:** S-11, 6장 알림(재확정)
- **선행 Task:** Task 039
- **선행 조건:** PRD 오픈 이슈 16(`ChangeLog` 공개 범위·민감 필드 화이트리스트 — 현재 안 그룹 멤버 전원 조회), 오픈 이슈 18(변동분 송금 경로 — 현재 안 변동액만 표시)
- **구현 사항**
  - [ ] `GET /api/meetings/{id}/settlement/revisions` — 리비전별 번호·확정자·시각·멤버별 부담액·순잔액·직전 대비 변동액·송금 안내
  - [ ] `GET /api/meetings/{id}/change-logs` — 시간순, 그룹 멤버만(비멤버 403 `NOT_GROUP_MEMBER`), 페이지네이션
  - [ ] `SettlementReconfirmedEvent`(커밋 후) — "정산이 바뀌었어요(리비전 n)" + 변동액 0 아닌 멤버 목록 + 새 송금 안내 + 안내 문구
  - [ ] `ShareTextBuilder`(순수) — "정산이 수정됐어요(리비전 2): 민수 −20,000원, 철수 +6,600원, … 이미 송금했다면 변동분만 주고받으세요 …", 금액 천 단위 구분·부호 표시
- **테스트**
  - [ ] 단위(`ShareTextBuilder`): 예 6 문구, 변동 0 멤버 제외, 부호·천 단위 구분
  - [ ] `@WebMvcTest`: 리비전 목록·변경 로그 응답 형식, 비멤버 403
  - [ ] 통합: 재확정 커밋 후 이벤트 1회, 롤백 시 없음
- **수락 기준**
  - 변동액이 0이 아닌 멤버 목록·변동액과 "이미 송금했다면 변동분만 앱 밖에서 주고받으세요" 안내가 "정산 재확정" 이벤트로 브로드캐스트되고 카톡 공유 문구가 생성된다. When 리비전 목록·변경 로그를 조회하면 / Then 그룹 멤버는 리비전별 확정자·시각·부담액·순잔액·직전 대비 변동액·송금 안내와 `ChangeLog` 항목을 시간순으로 볼 수 있고, 비멤버는 403(`NOT_GROUP_MEMBER`). (PRD S-11 뒷부분)
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

---

## Phase 10: MVP 통합 검증 (PRD 9단계)

- **목표:** API → DB → 계산 결과·리비전 스냅샷·변동액을 시나리오 단위로 검증하고, 동시성·실시간 보장을 실제 DB로 확인한 뒤 MVP 배포 준비
- **포함 기능 ID:** MVP P0 전체

### Task 041: 정산 시나리오 통합 테스트 (예 1~6)   ⬜ 대기

- **관련 기능:** S-1~S-5, S-8, S-10, S-11, R-5, R-9
- **선행 Task:** Task 040
- **구현 사항**
  - [ ] `@SpringBootTest` + MockMvc(또는 HTTP 클라이언트) 시나리오 픽스처: 그룹·모임·2차 참석 4명(철수·민수·영희·지수)·지출·게임(공개 순서 제어)·조건 설정을 API로 구성
- **테스트**
  - [ ] 예 1, 2, 3, 4-a, 4-b, 5-a, 5-b 각각: 정산 조회 응답의 1인 부담액·순잔액·송금 안내·경고 여부가 PRD 표와 일치
  - [ ] S-8 `FULL` 2명 케이스(경고 없음)
  - [ ] 예 6: 리비전 1 확정 → 조건 변경 → `REOPENED` → 재확정 → 리비전 2 스냅샷·`PenaltyCharge`·`Transfer`·변동액, 리비전 1 불변
  - [ ] 대상 지출 삭제 → `ASSIGNED` 복귀 → 확정 409 `PENALTY_CONDITION_UNSET` → 재설정 후 확정
- **수락 기준**
  - Given PRD 4.5 계산 예시 8케이스 / When API로 같은 상황을 구성하고 정산을 조회·확정하면 / Then 모든 금액·경고·송금 안내·변동액이 PRD 표와 원 단위까지 일치한다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 042: 동시성·실시간 통합 테스트   ⬜ 대기

- **관련 기능:** 6장 트랜잭션·동시성 락 표, 6장 실시간
- **선행 Task:** Task 040
- **구현 사항**
  - [ ] 멀티스레드 테스트 헬퍼(`CountDownLatch`/`ExecutorService`)와 실제 DB(Task 002 전략)
- **테스트**
  - [ ] 그룹 가입 동시 클릭 → 멤버 1건
  - [ ] 참석 응답 동시 → 1건, 재시도 facade 동작
  - [ ] 장소 마감 vs 수동 확정 → 1건 반영
  - [ ] 꽝뽑기 같은 카드·다른 카드 동시 선택, 마지막 선택 vs 자동 배정
  - [ ] 다시 돌리기 동시 2건 → 1건
  - [ ] 정산 확정 vs 지출 수정 / 조건 설정 / 금액형 게임 취소 → 직렬화(한쪽 409)
  - [ ] 롤백된 쓰기는 STOMP 메시지 0건, 커밋된 쓰기는 1건(실제 STOMP 클라이언트 또는 브로커 채널 캡처)
- **수락 기준**
  - Given 6장 락 표의 각 충돌 상황 / When 동시 요청을 보내면 / Then 표에 적힌 전략대로 하나만 반영되고 늦은 쪽은 명시된 409 코드를 받는다.
- **완료 조건**
  - [ ] `./gradlew test` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족

### Task 043: MVP 핵심 흐름 E2E·배포 전 점검   ⬜ 대기

- **관련 기능:** MVP P0 전체, 9장 리스크(이메일 가입 코드 잔존, 인앱 브라우저)
- **선행 Task:** Task 041, Task 042
- **구현 사항**
  - [ ] 3장 핵심 사용자 흐름 E2E: 그룹 생성 → 초대 → 가입 → 모임·차수 → 참석 → 장소 투표·확정 → 게임 → 지출 → 조건 → 확정 → 수정 → 재확정
  - [ ] 공개 경로·인가 점검표: 모든 그룹 리소스 API 비멤버 403, 호스트 전용 API 비호스트 403
  - [ ] 이메일 가입 경로 완전 제거 확인, 비밀값 설정 외부화 확인
  - [ ] 1장 지표 산출에 필요한 시각 필드(`Attendance.respondedAt`, `SettlementRevision.confirmedAt`, `Expense.createdAt`) 저장 확인
- **테스트**
  - [ ] E2E 시나리오 1개 이상(`@SpringBootTest`, 카카오 클라이언트는 스텁)
  - [ ] 인가 점검 파라미터화 테스트
- **수락 기준**
  - Given 새 그룹 / When 3장 흐름을 API로 끝까지 수행하면 / Then 각 단계 응답이 성공하고 최종 리비전 2의 송금 안내·변동액이 계산값과 일치한다.
- **완료 조건**
  - [ ] `./gradlew build` 통과 · [ ] `/spring-tools:validate` 통과 · [ ] 수락 기준 충족 · [ ] 카카오톡 인앱 브라우저 로그인 수동 확인

---

## Phase 11: v0.2 (개략)

검증 질문: 총무가 정산을 10분 안에 끝내나?

| Task | 제목 | 기능 ID | 선행 Task | 선행 조건 |
|---|---|---|---|---|
| Task 044 | 날짜 후보 투표(When2meet식) | M-5 | 043 | — |
| Task 045 | 미응답자 리마인드(카톡 공유 문구) | M-6 | 043 | 웹 푸시 부분은 Task 056과 연계 |
| Task 046 | 당일 체크인 → 실제 참석자 확정 | M-7 | 043 | — |
| Task 047 | 볼링 점수 입력 → 순위 → 하위 N명 자동 벌칙 | R-6 | 043 | 오픈 이슈 12(벌칙자별 규칙) |
| Task 048 | 송금 딥링크·계좌 복사 ⚠️ | S-7 | 043 | 오픈 이슈 3(딥링크 정책), 계좌 저장(G-5, Task 059)이 v0.3에 있어 순서 재조정 필요 |
| Task 049 | 지출 영수증 사진 | S-1(확장) | 043 | — |
| Task 050 | 1차 장소 반경 2차 장소 추천 | P-7 | 043 | 오픈 이슈 5 ⑤(좌표 실시간 획득) |

## Phase 12: v0.3 (개략)

검증 질문: 그룹이 매달 다시 오나?

| Task | 제목 | 기능 ID | 선행 Task | 선행 조건 |
|---|---|---|---|---|
| Task 051 | 명예의 전당/불명예의 전당 | H-2 | 043 | — |
| Task 052 | 모임 히스토리 | H-1 | 043 | — |
| Task 053 | 룰렛·랜덤 순서 정하기 | R-3 | 043 | — |
| Task 054 | 장소 동점 룰렛 결정 | P-6 | 053 | — |
| Task 055 | 보드게임 승패 기록 | R-7 | 043 | — |
| Task 056 | PWA 웹 푸시 | 6장 플랫폼, M-6 | 045 | — |
| Task 057 | 그룹 단골 장소·지난 장소 불러오기 | P-8 | 043 | 오픈 이슈 5 ③ |
| Task 058 | 그룹 역할(방장/총무/멤버) | G-4 | 043 | 오픈 이슈 15(호스트 위임) |
| Task 059 | 그룹 기본 송금 계좌·토스/카카오페이 링크 | G-5 | 043 | — |

> ⚠️ S-7(Task 048, v0.2)의 "계좌 복사"는 G-5(Task 059, v0.3)의 계좌 저장에 기대므로 PRD 릴리즈 배치와 실제 의존이 엇갈린다. v0.2 착수 시 G-5를 v0.2로 당기거나 S-7을 딥링크만으로 줄이는 결정 필요.

## Phase 13: v1.0 (개략)

검증 질문: 공유를 통한 신규 그룹 유입이 생기나?

| Task | 제목 | 기능 ID | 선행 Task | 선행 조건 |
|---|---|---|---|---|
| Task 060 | 모임 결산 카드(이미지) | H-3 | 052 | — |
| Task 061 | 연말 결산 | H-4 | 052 | — |
| Task 062 | 회비 통장 모드 | S-9 | 043 | — |
| Task 063 | 카카오 계정 없는 임시 게스트 | G-6 | 043 | — |
| Task 064 | 중간 지점 추천 | P-10 | 043 | 오픈 이슈 5 ④ |
| Task 065 | 행동형 벌칙 "다음 모임 장소 정하기" 권한 | P-9 | 043 | — |
| Task 066 | 노쇼 기록·통계 | M-8 | 046 | — |

---

## 기능 ID 매핑

| 기능 ID | 우선순위 | Task |
|---|---|---|
| G-1 | P0 | 007, 008 |
| G-2 | P0 | 007, 009 |
| G-3 | P0 | 003, 004, 005, 006, 009 |
| G-4 | P1 | 058 |
| G-5 | P1 | 059 |
| G-6 | P2 | 063 |
| M-1 | P0 | 011, 012 |
| M-2 | P0 | 011, 012 |
| M-3 | P0 | 011, 013, 025 |
| M-4 | P0 | 013 |
| M-5 | P1 | 044 |
| M-6 | P1 | 045, 056 |
| M-7 | P1 | 046 |
| M-8 | P2 | 066 |
| P-1 | P0 | 014, 015, 016 |
| P-2 | P0 | 014, 016 |
| P-3 | P0 | 010, 017 |
| P-4 | P0 | 018 |
| P-5 | P0 | 014, 019 |
| P-6 | P1 | 054 |
| P-7 | P1 | 050 |
| P-8 | P1 | 057 |
| P-9 | P2 | 065 |
| P-10 | P2 | 064 |
| R-0 | P0 | 010, 026, 027 |
| R-1 | P0 | 028 |
| R-2 | P0 | 029 |
| R-3 | P1 | 053 |
| R-4 | P0 | 027, 032, 036 |
| R-5 | P0 | 031, 032, 033, 034 |
| R-6 | P1 | 047 |
| R-7 | P1 | 055 |
| R-8 | P0 | 030 |
| R-9 | P0 | 031, 035, 036 |
| R-10 | P0 | 026, 027, 033 |
| S-1 | P0 | 020, 022, 037 (영수증 사진 확장 049) |
| S-2 | P0 | 023 |
| S-3 | P0 | 020, 022, 023 |
| S-4 | P0 | 037 |
| S-5 | P0 | 023, 024, 039 |
| S-6 | 폐지(E1) | — (Task 없음) |
| S-7 | P1 | 048 |
| S-8 | P0 | 023, 035 |
| S-9 | P2 | 062 |
| S-10 | P0 | 024, 038, 039 |
| S-11 | P0 | 021, 025, 039, 040 |
| H-1 | P1 | 052 |
| H-2 | P1 | 051 |
| H-3 | P2 | 060 |
| H-4 | P2 | 061 |

P0 26개 전부 MVP Phase(0~10)에 매핑됨. 통합 검증 Task 041~043은 P0 전체를 다시 검증한다.

## 오픈 이슈 연계

| PRD 오픈 이슈 | 영향 Task | 결정 필요 시점 |
|---|---|---|
| 1. SPA 프레임워크 | 006(쿠키 도메인·리다이렉트 오리진), 019(OG 경로·배포) | Phase 1 착수 전(설정값 분리로 진행 가능), Task 019 착수 전 확정 |
| 2. 벌칙자의 나머지 분배 제외 | 035 (PRD 예시·수용 기준이 현재 안 기준) | Phase 8 착수 전 현재 안 유지 확인 |
| 3. 송금 딥링크 정책 | 048 | v0.2 착수 전 |
| 4. 장소 투표 방식(복수 vs 1인 1표) | 017 | Task 017 착수 전 현재 안 유지 확인 |
| 5. 카카오 로컬 데이터 저장 범위 | 014, 016, 019 (⏸️), 050, 057, 064 | **Phase 4 착수 전 카카오 공식 회신 필수** |
| 6. 장소 투표를 참석과 묶을지 | 017 | Task 017 착수 전 |
| 8. S-8 초과분 차감 대상 애매한 경우 | 023, 035 | Task 023 착수 전(경계 케이스만 보류 가능) |
| 12. 벌칙자별 다른 조건 | 047 | v0.2 R-6 착수 전 |
| 14. 정산 잠금 시점 | — (MVP 잠금 없음) | 출시 후 재확정 비율 지표 보고 |
| 15. 호스트 위임·부재 | 058 | v0.3 G-4 설계 시 |
| 16. `ChangeLog` 공개 범위·화이트리스트 | 021, 040 | Task 021 착수 전 |
| 17. `REOPENED` 판단 기준 | 021, 025 | Task 021 착수 전 현재 안 유지 확인 |
| 18. 변동분 송금 경로 안내 | 040 (현재 안: 변동액만) | 출시 후 |
| 19. 정산 시간 지표 "모임 종료" 기준 | 043(지표용 시각 필드 확인) | 출시 후 |
| 7, 9, 10, 11, 13 | — (종결) | — |
