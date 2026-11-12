# moyeo ROADMAP

> 기준 PRD: docs/PRD.md (기준일 2026-10-26, 커밋 `0500d24` "PRD 검증 반영: 벌칙·정산 모델 개정" 이후 상태, 2026-11-04 오픈 이슈 5 종결 — 카카오 데이터 무저장 반영) · 마지막 갱신: 2026-11-12 (`feature/game-rerun` 기준)

## 개요

- **목표:** "모임 공지 → 장소 정하기 → 참석 → 놀기(게임) → 정산"을 하나의 링크로 끝내는 모바일 웹앱의 백엔드(JSON API + WebSocket) MVP(v0.1) 완성
- **현재 단계:** Phase 7 진행 중(Task 033 완료, 2026-11-12) — Task 015(카카오 로컬 검색 프록시) 완료. 오픈 이슈 5를 "카카오 데이터 무저장"으로 종결(2026-11-04)해 Task 014·016·019 보류를 풀었다. Task 014(장소 값 타입·후보·투표 도메인)·Task 016(후보 등록·바로 확정·투표 열기·재투표) 완료(2026-11-04), Task 017(후보 투표·취소·실시간 득표 현황) 완료(2026-11-05), Task 018(투표 마감 자동 확정·호스트 수동 확정) 완료(2026-11-07). Task 019(확정 장소 노출·OG 메타 HTML) 완료(2026-11-08, 오픈 이슈 1과 무관한 `/share/**` 경로로 구현). Phase 5(정산 기본·최초 확정)는 Task 025로 완료했다. Phase 6(게임 방·사다리·꽝뽑기)은 Task 030으로 완료했다. Phase 7은 Task 031(벌칙·벌칙 규칙 도메인 골격, 2026-11-11)과 Task 032(결과 공개 시 벌칙자 확정, 2026-11-12), Task 033(다시 돌리기 요청과 다시 돌린 회차 취소, 2026-11-12)을 완료했다. 다음은 Task 034(다시 돌린 회차 결과 공개). 코드에는 카카오 기반 회원·내 정보 API(`member/`), 카카오 로그인·콜백·토큰 재발급/로그아웃 API와 쿠키 JWT 인증 필터(`auth/`), 그룹·그룹 멤버·초대 링크 도메인과 그룹 생성·내 그룹 목록·초대 링크 재발급·초대 미리보기/가입·멤버 목록·그룹 프로필 변경 API(`group/`), 그룹 멤버 인가 `GroupAccessChecker`(`global/auth/`), STOMP 설정·구독 인가(`global/websocket/`)와 커밋 후 브로드캐스트(`global/event/`), 모임·차수·참석 엔티티, 모임 토픽 해석기, 모임 생성·상세·그룹 모임 목록 API와 차수별 참석 응답·호스트 대리 수정·현황판 API(`meeting/`), 모임 인가 `MeetingAccessChecker`(`global/auth/`), 정산 골격·지출·분배 조정·변경 로그 엔티티와 정산 직렬화·`REOPENED` 전이·변경 로그 기록과 지출 등록·수정·삭제·분배 조정·목록 API(`settlement/`), 벌칙 제외 정산 계산·송금 최소화 순수 도메인(`settlement/calc/`), 정산 조회·최초 확정 API와 리비전 스냅샷, 참석 변경의 정산 반영(`settlement/`), 낙관적 락 재시도 실행기(`global/concurrency/`), 카카오 로컬 장소 검색 프록시·회원별 빈도 제한과 장소 값 타입·후보·투표 엔티티·`@HttpsUrl`·지도 링크 생성기와 후보 추가·바로 확정·투표 열기(재투표) API·차수 인가와 후보 투표·취소·득표 현황 API·투표 마감 스케줄러·호스트 수동 확정 API(`place/`), 확정 장소 노출과 공유 링크 OG 메타 HTML(`og/`), 게임·참가자·결과 엔티티와 상태 전이, 게임 토픽 해석기, 게임 방 생성·즉시 추첨·종료·취소 API와 게임 상태 조회·차수별 게임 목록 API, 다시 돌리기 요청 API(`game/`), 게임 인가 `GameAccessChecker`(`global/auth/`), 벌칙·벌칙 금액 조건 엔티티와 벌칙 상태 전이, 결과 공개 시 벌칙자 확정 `PenaltyAssigner`(`penalty/`), 공통 코드(`global/`)가 있다.
- **구현 순서:** PRD 8장 "MVP 구현 순서(백엔드)" 0 → 1 → 2 → 3 → 4 → 5-a → 6 → 7 → 8 → 5-b → 9를 그대로 따른다. 실시간 기반(WebSocket)은 P-3(4단계)의 수용 기준이 요구하므로 3단계 앞으로 당겼다(Task 010).

| Phase | 이름 | PRD 단계 | Task 수 | 완료 | 상태 |
|---|---|---|---|---|---|
| 0 | 기반 정리 | 0 | 3 | 3 | ✅ 완료 |
| 1 | 카카오 로그인·토큰 | 1 | 3 | 3 | ✅ 완료 |
| 2 | 그룹·초대 | 2 | 3 | 3 | ✅ 완료 |
| 3 | 실시간 기반·모임·차수·참석 | 3 | 4 | 4 | ✅ 완료 |
| 4 | 장소 결정 | 4 | 6 | 6 | ✅ 완료 |
| 5 | 정산 기본·최초 확정 | 5-a | 6 | 6 | ✅ 완료 |
| 6 | 게임 방·사다리·꽝뽑기 | 6 | 5 | 5 | ✅ 완료 |
| 7 | 벌칙자 확정·다시 돌리기 | 7 | 4 | 3 | 🔄 진행 중 |
| 8 | 벌칙 정산 연동·확정 게이트 | 8 | 4 | 0 | ⬜ 대기 |
| 9 | 재확정·리비전 비교·알림 | 5-b | 2 | 0 | ⬜ 대기 |
| 10 | MVP 통합 검증 | 9 | 3 | 0 | ⬜ 대기 |
| 11 | v0.2 (개략) | — | 7 | 0 | ⬜ 대기 |
| 12 | v0.3 (개략) | — | 9 | 0 | ⬜ 대기 |
| 13 | v1.0 (개략) | — | 7 | 0 | ⬜ 대기 |
| **합계** | | | **66** (MVP 43) | **33** | |

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
Phase 6  013, 021 → 026 → 027(028·029 흡수) → 030
Phase 7  026 → 031 → 032 (← 027) → 033 → 034
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

### Task 004: 카카오 OAuth 클라이언트 (인가 코드 → 사용자 정보)   ✅ 완료

- **관련 기능:** G-3, 6장 인증
- **선행 Task:** Task 003
- **구현 사항**
  - [x] `auth/client/KakaoOAuthClient` — 인가 코드로 토큰 요청(`kauth.kakao.com/oauth/token`), 사용자 정보 조회(`kapi.kakao.com/v2/user/me`). `RestClient` 사용(Boot 4 스타터 이름은 Context7로 확인)
  - [x] `auth/config/KakaoOAuthProperties`(`@ConfigurationProperties`: clientId, clientSecret, redirectUri, 타임아웃) — 비밀값은 `application-local.properties`·환경 변수로만
  - [x] `auth/dto/KakaoUserInfo`(kakaoId, nickname, profileImageUrl, email?) — 동의 안 한 항목은 null 허용. `toProfile()`로 `member/dto/KakaoProfile`로 변환해 `MemberService`에 넘긴다
  - [x] 연결·읽기 타임아웃 설정
  - [x] 에러 코드: `KAKAO_AUTH_FAILED`(401, 잘못된·만료된 인가 코드), `KAKAO_API_UNAVAILABLE`(502/503, 카카오 장애·타임아웃)
- **테스트**
  - [x] 목 서버(`MockRestServiceServer` 등) 기반: 성공 시 `KakaoUserInfo` 매핑(이메일 없는 응답 포함)
  - [x] 실패: 토큰 요청 400/401 → `KAKAO_AUTH_FAILED`
  - [x] 실패: 5xx·타임아웃 → `KAKAO_API_UNAVAILABLE`
  - [x] 클라이언트 시크릿이 로그에 남지 않는지 확인
- **수락 기준**
  - Given 유효한 인가 코드(스텁) / When 클라이언트를 호출하면 / Then kakaoId·닉네임·프로필 이미지가 담긴 `KakaoUserInfo`를 받는다. Given 카카오 응답 지연 / When 타임아웃이 지나면 / Then `KAKAO_API_UNAVAILABLE` 예외가 난다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 39건, `KakaoOAuthClientTest` 9건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족(타임아웃은 `SocketTimeoutException` 스텁으로 재현)

### Task 005: 자체 토큰(Access·Refresh) 발급·검증   ✅ 완료

- **관련 기능:** G-3, 6장 인증
- **선행 Task:** Task 004
- **구현 사항**
  - [x] 인증 처리 방식 결정·적용 — `spring-boot-starter-security` 무상태 필터 체인(`global/config/SecurityConfig`, 지금은 `permitAll`, 인증 필터는 Task 006) + jjwt 0.13 HS256. CSRF: 인증 쿠키를 `SameSite=Lax`로 내리고 상태 변경 GET을 금지하는 대신 CSRF 토큰은 끈다
  - [x] `auth/token/JwtTokenProvider` — Access 발급·검증(subject = memberId), 만료 시간 설정값(`auth/config/JwtProperties`: `auth.jwt.secret` 32바이트 이상 필수, Access 30분·Refresh 14일 기본)
  - [x] `auth/domain/RefreshToken` 엔티티(member, tokenHash, expiresAt) + `RefreshTokenRepository` — 재발급 시 회전(기존 토큰 폐기). 원문은 `RefreshTokenGenerator`(SecureRandom 32바이트), DB에는 SHA-256 hex만. `auth/service/TokenService`의 `issue`·`reissue`·`revoke`, 동시 회전은 벌크 삭제 건수 0으로 감지
  - [x] `auth/token/TokenCookieFactory` — `HttpOnly`·`Secure`·`SameSite=Lax` 쿠키 생성/삭제(Access `Path=/`, Refresh `Path=/api/auth`). 설정 `auth.cookie.domain`(오픈 이슈 1 결정 후)·`auth.cookie.secure`(로컬만 false)
  - [x] 에러 코드: `INVALID_TOKEN`(401), `EXPIRED_TOKEN`(401), `INVALID_REFRESH_TOKEN`(401)
- **테스트**
  - [x] 단위: Access 발급 → 검증 성공, 서명 위조·만료 토큰 → 각 에러 코드(고정 `Clock`)
  - [x] `@DataJpaTest`: `RefreshToken` 저장·조회·삭제, 토큰 원문이 아닌 해시가 저장되는지
  - [x] 단위: Refresh 회전 — 재발급 후 이전 Refresh로 재요청하면 `INVALID_REFRESH_TOKEN`
- **수락 기준**
  - Given 발급된 Refresh 토큰 / When 재발급을 요청하면 / Then 새 Access·Refresh가 발급되고 이전 Refresh는 더 이상 쓸 수 없다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 80건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족(`TokenServiceIntegrationTest`로 실제 DB에서 확인)
- **참고:** 시큐리티가 들어오면서 `@WebMvcTest`는 `@Import(SecurityConfig.class)`가 있어야 기본 체인(인증 필요·CSRF)을 피한다. 로컬 실행에는 `application-local.properties`에 `auth.jwt.secret`·`auth.cookie.secure=false`가 필요하다

### Task 006: 인증 API·인증 필터·로그인 후 원래 링크 복귀   ✅ 완료

- **관련 기능:** G-3, 6장 인증·인가(카카오톡 인앱 브라우저)
- **선행 Task:** Task 005
- **선행 조건:** ⚠️ PRD 오픈 이슈 1(SPA 프레임워크·배포 방식) — 쿠키 도메인·리다이렉트 대상 오리진 설정에 영향. 설정값(`auth.cookie.domain`, `auth.redirect.frontend-base-url`)으로 분리해 뒀고, 비우면 같은 오리진 상대 경로로 동작한다. 결정 후 채운다
- **구현 사항**
  - [x] `auth/controller/AuthController`(`@RestController`)
    - [x] `GET /api/auth/kakao/login?returnUrl=` — 카카오 인가 URL로 302. `state`(SecureRandom 32바이트)와 검증된 `returnUrl`은 `auth/support/OAuthStateCookies`가 `HttpOnly` 쿠키(`Path=/api/auth/kakao`, 10분)로 내려 콜백에서 꺼낸다
    - [x] `GET /api/auth/kakao/callback` — `state` 상수 시간 비교 → `AuthService.loginWithKakao`(`KakaoOAuthClient` → `MemberService.findOrCreateByKakaoId` → `TokenService.issue`) → 토큰 쿠키 + state 쿠키 만료 → `returnUrl`로 302. 동의 취소(`error`)·code 없음은 `KAKAO_AUTH_FAILED`
    - [x] `POST /api/auth/refresh`(회전), `POST /api/auth/logout`(Refresh 삭제·쿠키 만료, 비로그인도 성공)
  - [x] `member/controller/MemberController`(`@RestController`) `GET /api/members/me` — `MemberService.getMe`(읽기 전용, 없으면 `MEMBER_NOT_FOUND` 404)
  - [x] 인증 필터 `auth/security/JwtAuthenticationFilter`(`access_token` 쿠키 검증) + `RestAuthenticationEntryPoint`(401 공통 JSON, 만료면 `EXPIRED_TOKEN`) + `@LoginMember Long memberId`(`@AuthenticationPrincipal` 메타 어노테이션)
  - [x] `returnUrl` 검증 `auth/support/ReturnUrlValidator`: `/`로 시작하는 상대 경로만 허용(`//` 시작·역슬래시·줄바꿈·길이 초과 거부, 오픈 리다이렉트 방지), 아니면 `auth.redirect.default-path`
  - [x] 공개 경로 화이트리스트(`SecurityConfig`): 로그인·콜백·재발급·로그아웃·초대 링크 조회(`GET /api/invites/*`). OG 메타 HTML 경로는 Task 019에서 추가
  - [x] 에러 코드: `INVALID_OAUTH_STATE`(401), `MEMBER_NOT_FOUND`(404)
- **테스트**
  - [x] `@WebMvcTest`: 콜백 성공 시 `Set-Cookie`에 `HttpOnly` Access·Refresh, `returnUrl`로 302 (`AuthControllerTest` 11건)
  - [x] `@WebMvcTest`: `state` 불일치·state 쿠키 없음 → 401 `INVALID_OAUTH_STATE`, 외부 도메인 `returnUrl` → 기본 경로로 리다이렉트
  - [x] `@WebMvcTest`: 토큰 없이 `/api/members/me` → 401 `UNAUTHORIZED` JSON, 만료 토큰 → 401 `EXPIRED_TOKEN`, 유효 토큰 → 내 정보 (`MemberControllerTest` 4건, `SecurityIntegrationTest` 6건)
  - [x] 카카오 클라이언트는 목 처리(실제 호출 없음)
- **수락 기준**
  - [x] Given 미로그인 사용자가 초대 링크 경로를 `returnUrl`로 로그인 시작 / When 카카오 로그인을 마치면 / Then 토큰 쿠키가 발급되고 원래 초대 링크 경로로 돌아간다(G-3 앞부분). — `AuthControllerTest` 콜백 성공(`Location: /invites/abc`)
  - [x] Given 로그인 회원 / When `/api/members/me`를 호출하면 / Then 닉네임·프로필 이미지가 공통 응답 래퍼로 반환된다. — `MemberControllerTest`(kakaoId·이메일 미노출 포함)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 129건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족 · [x] 카카오톡 인앱 브라우저에서 로그인·리다이렉트 수동 확인(9장 리스크) — 2026-10-31, cloudflared HTTPS 터널 + `auth.cookie.secure=true`로 확인: ① `returnUrl=/api/members/me` 로그인 후 내 정보 JSON, ② 인앱 브라우저를 닫았다 다시 열어도 쿠키 유지, ③ `/invites/abc` 경로 유지, ④ 외부 주소 `returnUrl`은 `/`로 차단. 확인 기기는 1대라 다른 OS(iOS/Android)는 배포 후 다시 본다
- **참고:** 로컬 실행에는 `application-local.properties`에 카카오 키(`kakao.oauth.*`)·`auth.jwt.secret`·`auth.cookie.secure=false`가 필요하다. 수동 확인 중 카카오 동의항목에 닉네임이 없으면 닉네임이 null로 와 회원 저장이 실패하고 `DUPLICATE_RESOURCE`(409)로 잘못 응답하는 문제를 발견했다 → `Member`가 신규 회원은 기본 닉네임(`모여 회원`), 기존 회원은 원래 닉네임을 쓰도록 수정했다(`feature/kakao-default-nickname`). 콘솔 동의항목은 닉네임 필수로 둔다. 테스트는 `application-test.properties`에서 `auth.redirect.frontend-base-url`을 비워 상대 경로 리다이렉트를 기대한다

---

## Phase 2: 그룹·초대

- **목표:** 그룹 생성, 초대 링크 발급·만료·재발급, 링크로 자동 가입, 그룹 멤버 인가(`NOT_GROUP_MEMBER`) 공통화
- **포함 기능 ID:** G-1, G-2, G-3

### Task 007: 그룹·멤버·초대 링크 도메인 골격   ✅ 완료

- **관련 기능:** G-1, G-2, G-3
- **선행 Task:** Task 006
- **구현 사항**
  - [x] `group/domain/Group`(name) — 테이블명 `friend_group`(`GROUP`·`GROUPS` 모두 MySQL 8 예약어라 `groups`도 피함)
  - [x] `group/domain/GroupMember`(group, member, nickname, emoji, role) — `(group_id, member_id)` 유니크 제약. `role`(`GroupRole`: OWNER/MEMBER)은 G-4(P1) 대비 컬럼만, MVP 로직 없음
  - [x] `group/domain/InviteLink`(group, token 유니크 43자, expiresAt, revokedAt?) — `issue`, `revoke(now)`(이미 폐기면 처음 시각 유지), `isUsable(now)`(만료 시각 정각부터 불가). 토큰은 공개 URL 값이라 해시 없이 원문 저장
  - [x] Repository: `GroupRepository`, `GroupMemberRepository.existsByGroupIdAndMemberId`, `findAllByGroupId`, `InviteLinkRepository.findByToken`
  - [x] 토큰 생성기 `group/token/InviteTokenGenerator`(`SecureRandom` 32바이트 → Base64URL 패딩 없음 43자)
- **테스트**
  - [x] `@DataJpaTest`: `(group, member)` 유니크 위반 시 `DataIntegrityViolationException` (`GroupMemberRepositoryTest` 4건)
  - [x] `@DataJpaTest`: token 유니크, `findByToken` (`InviteLinkRepositoryTest` 3건)
  - [x] 단위: `InviteLink.isUsable` — 만료 직전·만료 시각 정각·만료 후, 폐기된 링크 (`InviteLinkTest` 5건) + `InviteTokenGeneratorTest` 2건
- **수락 기준**
  - Given 같은 그룹·회원 / When `GroupMember`를 두 번 저장하면 / Then 두 번째는 유니크 제약 위반이다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 146건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족

### Task 008: 그룹 생성·초대 링크 발급/재발급·그룹 멤버 인가 공통화   ✅ 완료

- **관련 기능:** G-1, 6장 인가
- **선행 Task:** Task 007
- **구현 사항**
  - [x] `group/service/GroupService.create` — 그룹 생성 + 생성자 `GroupMember`(닉네임 기본값 = 카카오 닉네임) + 초대 링크 발급, 한 트랜잭션
  - [x] `InviteLinkService.reissue` — 기존 링크 폐기(`revokedAt`) + 새 토큰 발급
  - [x] ⚠️ 초대 링크 만료 기간 기본값·재발급 권한 — PRD 미정이라 결정(2026-11-01): 만료 **7일**(`group.invite.ttl`, `InviteLinkProperties`), 재발급은 **그룹 멤버 누구나**. 공유 URL 앞부분은 `group.invite.base-url`(오픈 이슈 1 결정 후 채움)
  - [x] `global/auth/GroupAccessChecker.requireMember(groupId, memberId)` → 403 `NOT_GROUP_MEMBER` (이후 모든 그룹 리소스 Service가 사용)
  - [x] `GroupController`: `POST /api/groups`, `POST /api/groups/{groupId}/invite-links`(재발급), `GET /api/groups`(내 그룹 목록)
  - [x] DTO: `GroupCreateRequest`(name `@NotBlank`), `GroupResponse`, `InviteLinkResponse`(token, url, expiresAt), 생성 응답은 `GroupCreateResponse`(group + inviteLink)
  - [x] 에러 코드: `NOT_GROUP_MEMBER`(403), `GROUP_NOT_FOUND`(404)
- **테스트**
  - [x] 단위(`GroupService`): 생성 시 생성자가 멤버가 되고 만료 시각 있는 링크가 발급됨
  - [x] 단위: 재발급 후 이전 토큰은 `isUsable=false`
  - [x] 단위(`GroupAccessChecker`): 비멤버 → `NOT_GROUP_MEMBER`
  - [x] `@WebMvcTest`: 그룹 이름 누락 400 + 필드 에러, 생성 성공 응답 형식
- **수락 기준**
  - Given 로그인한 사용자 / When 그룹을 생성하면 / Then 생성자는 그 그룹 멤버가 되고 만료 시각이 있는 초대 링크 토큰이 발급된다. Given 초대 링크를 재발급하면 / Then 이전 토큰으로 접속 시 "만료된 링크" 응답을 받는다. (PRD G-1)
- **완료 조건**
  - [x] `./gradlew test` 통과 · [x] `/spring-tools:validate` 통과 · [x] 수락 기준 충족

### Task 009: 초대 링크 자동 가입·멤버 목록·그룹 프로필   ✅ 완료

- **관련 기능:** G-2, G-3
- **선행 Task:** Task 008
- **구현 사항**
  - [x] `GET /api/invites/{token}` (비로그인 허용) — `InvitePreviewResponse`(groupName, memberCount, expiresAt, 그룹 id는 비노출). 없음 404 `INVITE_LINK_NOT_FOUND`, 만료·폐기 **410** `INVITE_LINK_EXPIRED`(결정 2026-11-01: 404와 구분)
  - [x] `POST /api/invites/{token}/join` (로그인 필요) — `InviteJoinResponse(groupId, joined)`. 이미 멤버면 생성 없이 `joined=false`(멱등), 아니면 `GroupMember` 생성(닉네임 기본값 = 카카오 닉네임, role `MEMBER`). 동시 클릭은 `InviteJoiner`(트랜잭션) 유니크 위반을 `InviteJoinService`가 잡아 새 트랜잭션으로 재확인
  - [x] `GET /api/groups/{groupId}/members` — 가입 순 `GroupMemberResponse`(memberId, nickname, emoji, role), 비멤버 403 `NOT_GROUP_MEMBER`
  - [x] `PATCH /api/groups/{groupId}/members/me` — 닉네임 `@NotBlank` 50자·앞뒤 공백 제거, 이모지 16자
  - [x] ⚠️ 이모지 기본값·허용 범위 — PRD 미정이라 결정(2026-11-01): 가입 시 **null**(프론트가 기본 아이콘 표시), 변경은 **16자 이내 자유 문자열**, 공백이면 null로 비움
  - [x] 에러 코드: `INVITE_LINK_EXPIRED`(410), `INVITE_LINK_NOT_FOUND`(404)
  - 로그인 후 원래 링크 복귀는 기존 `returnUrl` 쿠키(Task 005)로 처리 — SPA가 초대 경로를 `returnUrl`로 넘기고 복귀 후 `join` 호출
- **테스트**
  - [x] 단위(`InviteJoiner`): 신규 가입 / 이미 멤버 재진입(중복 생성 없음) / 만료 링크 거부 / 회원 없음
  - [x] 단위(`InviteJoinService`): 유니크 위반 예외 발생 시 기존 멤버로 성공 응답, 재시도도 실패하면 전파
  - [x] 단위(`InviteLinkService`·`GroupMemberService`): 미리보기, 없는 토큰 404, 만료 정각·폐기 410, 프로필 변경·이모지 비우기, 비멤버 403
  - [x] `@DataJpaTest`: 멤버 목록(가입 순·다른 그룹 제외), 멤버십 단건 조회, 멤버 수, 토큰+그룹 fetch join
  - [x] `@WebMvcTest`: 비로그인 미리보기 200·410·404, 비로그인 가입 401, 비멤버 멤버 목록 조회 403 `NOT_GROUP_MEMBER`, 닉네임 공백 400, 길이 초과 400
- **수락 기준**
  - Given 미로그인 사용자가 유효한 초대 링크로 접속 / When 카카오 로그인을 마치면 / Then 원래 초대 링크로 돌아가 그룹에 자동 가입되고 그룹 닉네임 기본값은 카카오 닉네임이다. Given 이미 가입된 멤버 / When 같은 링크로 다시 들어오면 / Then 중복 가입 없이 그룹 홈으로 이동한다. (PRD G-3)
  - Given 그룹 멤버 / When 멤버 목록을 조회하면 / Then 각 멤버의 그룹 내 닉네임·프로필 이모지가 반환되고, 비멤버가 조회하면 403(`NOT_GROUP_MEMBER`)을 받는다. (PRD G-2)
- **완료 조건**
  - [x] `./gradlew test` 통과 · [x] `/spring-tools:validate` 통과 · [x] 수락 기준 충족

---

## Phase 3: 실시간 기반·모임·차수·참석

- **목표:** WebSocket(STOMP) 공통 기반, 모임 생성(호스트 = 생성자, `Settlement` `DRAFT` 동시 생성), 차수 템플릿, 차수별 참석 응답·마감·현황판
- **포함 기능 ID:** M-1, M-2, M-3, M-4, 6장 실시간

### Task 010: WebSocket(STOMP) 기반과 커밋 후 브로드캐스트 공통 구성   ✅ 완료

- **관련 기능:** 6장 실시간(참석·장소 투표·게임·정산 공통 채널), P-3·R-0 전제
- **선행 Task:** Task 006
- **구현 사항**
  - [x] 의존성 `spring-boot-starter-websocket`(Boot 4.1 문서로 이름 확인), `global/websocket/WebSocketConfig`(`@EnableWebSocketMessageBroker`, 엔드포인트 `/ws` 네이티브 WebSocket·SockJS 없음, simple broker `/topic`, 앱 prefix `/app`)
  - [x] 핸드셰이크 인증: 별도 인터셉터 없이 기존 시큐리티 체인 재사용 — `/ws`는 인증 필요 경로라 `JwtAuthenticationFilter`가 `access_token` 쿠키를 검증(없거나 무효면 401), 그 `Authentication`(이름 = memberId)이 STOMP `Principal`이 된다
  - [x] 구독 인가 `SubscriptionAuthorizationInterceptor`(`ChannelInterceptor`): SUBSCRIBE는 토픽 → 그룹을 `TopicGroupResolver`로 해석해 `GroupAccessChecker.requireMember` 재사용(없는 리소스도 `NOT_GROUP_MEMBER`), 해석기 없는 토픽은 기본 거부, 클라이언트의 `/topic` SEND 거부. 모임·게임 해석기는 Task 011·026에서 구현
  - [x] `StompErrorHandler`: ERROR 프레임 `message` 헤더에 에러 코드 이름(예: `NOT_GROUP_MEMBER`), 본문에 사용자용 메시지. 알 수 없는 예외는 `INTERNAL_ERROR`로 감춤
  - [x] `global/event/RealtimeEvent`(destination, type, data) + `RealtimeMessage(type, data)` 공통 봉투 + `RealtimeBroadcaster`(`@TransactionalEventListener(phase = AFTER_COMMIT)` → `SimpMessageSendingOperations`). 트랜잭션 밖 발행은 무시(`fallbackExecution=false`), 전송 실패는 로그만
  - [x] 토픽 규약 `RealtimeTopics`(클래스 주석): 참석·장소·정산 = `/topic/meetings/{meetingId}`, 게임 = `/topic/games/{gameId}`
  - [x] 허용 오리진 `realtime.websocket.allowed-origins`(`RealtimeProperties`, 비우면 같은 오리진만) — 오픈 이슈 1 결정 후 채움
  - [x] 에러 코드: `REALTIME_DESTINATION_FORBIDDEN`(403)
- **테스트**
  - [x] 단위(`SubscriptionAuthorizationInterceptorTest` 7건): 멤버 허용, 비멤버·없는 모임 `NOT_GROUP_MEMBER`, 해석기 없는 토픽·`/topic` SEND 거부, Principal 없음 `UNAUTHORIZED`, CONNECT·`/app` SEND 통과
  - [x] 단위(`StompErrorHandlerTest` 2건): 에러 코드·receipt-id 전달, 알 수 없는 예외 감춤
  - [x] 통합(`RealtimeBroadcasterIntegrationTest` 3건, `@MockitoSpyBean SimpMessagingTemplate`): 커밋 시에만 1회 전송, 롤백·트랜잭션 밖 발행 시 전송 없음
  - [x] 통합(`WebSocketIntegrationTest` 3건, 실제 포트 + `WebSocketStompClient`, 테스트 전용 토픽 해석기): 쿠키 없는 핸드셰이크 401, 멤버는 커밋된 이벤트 수신, 비멤버 구독은 ERROR 프레임 `NOT_GROUP_MEMBER`
- **수락 기준**
  - [x] Given 이벤트를 발행한 트랜잭션이 롤백 / When 처리가 끝나면 / Then 어떤 브로드캐스트도 나가지 않는다. Given 비멤버 / When 모임 토픽을 구독하면 / Then 거부된다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 214건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족
- **참고:** `@MockitoBean SimpMessageSendingOperations`로 바꾸면 내부 빈들이 `SimpMessagingTemplate` 타입을 요구해 컨텍스트 로딩이 실패하므로 스파이를 쓴다. 연결 중 Access 토큰이 만료돼도 연결은 끊지 않는다(인가는 구독 시점에만, MVP 허용) — 재연결 시 쿠키 재검증. 서버 발행 메시지는 `application/json`이라 클라이언트는 JSON 컨버터로 받는다

### Task 011: 모임·차수·참석·정산(골격) 도메인   ✅ 완료

- **관련 기능:** M-1, M-2, M-3, S-10(정산 상태 골격), D4
- **선행 Task:** Task 009
- **구현 사항**
  - [x] `meeting/domain/Meeting`(group, title 100자, startAt, memo 1000자?, responseDeadline?, hostMemberId 불변) + `isHost(memberId)`(null이면 false). ⚠️ 제목·메모 길이는 PRD 미정이라 결정(2026-11-02)
  - [x] `meeting/domain/Round`(meeting, seq, name 30자, placeStatus `UNDECIDED/VOTING/CONFIRMED`(생성 시 `UNDECIDED`), placeVoteDeadline?, `@Version version`) — 테이블명 `meeting_round`(SQL `ROUND` 함수와 혼동 회피), `(meeting, seq)` 유니크. 차수 "name/kind"는 PRD에 종류 enum 정의가 없어 **`name` 문자열 하나**로 결정. `confirmedCandidate`는 FK 대상 `PlaceCandidate`가 없어 **Task 014로 이동**, 장소 상태 전이 메서드는 Task 014·016에서
  - [x] `meeting/domain/Attendance`(round, member, status `ATTEND/ABSENT/UNDECIDED`, checkedIn 기본 false, respondedAt) — `(round, member)` 유니크. `respond`·`changeStatus(status, now)`(Task 013 upsert용)
  - [x] `settlement/domain/Settlement`(meeting `@OneToOne` 유니크, status `DRAFT/CONFIRMED/REOPENED`, currentRevisionNo?, firstConfirmedAt?, `@Version version`) — `createDraft`로 `DRAFT` 생성. 전이 메서드는 Task 021·024
  - [x] Repository 4종 + `RoundRepository.findAllByMeetingIdOrderBySeq`, `MeetingRepository.findGroupIdById`(해석기용 그룹 id만 조회)
  - [x] 모임 토픽 해석기 `meeting/service/MeetingTopicGroupResolver`(`/topic/meetings/{meetingId}` → 모임의 groupId). 없는 모임·숫자 아님·하위 경로·0·앞자리 0·19자리 이상은 조회 없이 null(→ `NOT_GROUP_MEMBER`)
- **테스트**
  - [x] `@DataJpaTest`: `(round, member)` 유니크·다른 차수 허용·체크인 기본값(`AttendanceRepositoryTest` 2건), `Settlement.meeting` 유니크·생성 기본값(`SettlementRepositoryTest` 2건), 차수 seq 순 조회·`(meeting, seq)` 유니크·`UNDECIDED` 기본값(`RoundRepositoryTest` 3건), `findGroupIdById`(`MeetingRepositoryTest` 1건)
  - [x] 단위: `Meeting.isHost`(`MeetingTest` 3건), `MeetingTopicGroupResolverTest` 10건(supports, 해석, 없는 모임, 형식 오류 7종)
- **수락 기준**
  - [x] Given 한 모임 / When `Settlement`를 두 건 저장하면 / Then 유니크 제약 위반이다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 235건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족

### Task 012: 모임 생성·차수 템플릿·호스트 권한 공통화   ✅ 완료

- **완료일:** 2026-11-02 · 브랜치 `feature/meeting-create`
- **관련 기능:** M-1, M-2, D4
- **선행 Task:** Task 011
- **구현 사항**
  - [x] `meeting/service/MeetingService.create` — 그룹 멤버 검사 → `Meeting`(hostMemberId = 요청자) + 차수 목록(seq 1..N) + `Settlement(DRAFT)`, 한 트랜잭션
  - [x] `meeting/domain/RoundTemplate` enum — ⚠️ PRD 예시만 있어 결정(2026-11-02): `MEAL_ONLY`(밥), `MEAL_BOWLING`(밥→볼링), `MEAL_BOARD_GAME`(밥→보드게임), `MEAL_BOWLING_BOARD_GAME`(밥→볼링→보드게임). 요청은 `template` 또는 직접 구성 `rounds`(이름 목록) 중 **정확히 하나**(아니면 400 `roundSpecValid` 필드 에러), 차수 **1~5개**, 이름 30자·앞뒤 공백 제거
  - [x] `global/auth/MeetingAccessChecker` — `requireMember(meetingId, memberId)`(그룹 멤버, 통과 시 `Meeting` 반환), `requireHost` → 403 `NOT_MEETING_HOST`. 결정(2026-11-02): **없는 모임도 403 `NOT_GROUP_MEMBER`**(존재 여부 숨김, 그룹 검사·모임 토픽 해석기와 일관)
  - [x] `MeetingController`: `POST /api/groups/{groupId}/meetings`(201), `GET /api/meetings/{meetingId}`(상세: 차수·장소 상태·호스트 여부), `GET /api/groups/{groupId}/meetings`(`upcoming` 가까운 순 / `past` 최근 순, 주입 `Clock` 기준, 시작 시각 정각은 다가오는 쪽)
  - [x] DTO: `MeetingCreateRequest`(title `@NotBlank` 100자, startAt `@NotNull`, memo 1000자, responseDeadline?, template?, rounds?), `MeetingDetailResponse`(url = 모임 상세 링크, host), `RoundResponse`, `MeetingSummaryResponse`, `GroupMeetingsResponse`
  - [x] 모임 상세 링크 `meeting/config/MeetingLinkProperties`(`meeting.link.base-url`, 비우면 `/meetings/{id}` 상대 경로 — 오픈 이슈 1 결정 후 채움)
  - [x] 에러 코드: `NOT_MEETING_HOST`(403), `MEETING_NOT_FOUND`(404), `ROUND_NOT_FOUND`(404) — 뒤의 둘은 코드만 추가(이후 Task 내부 조회용)
- **테스트**
  - [x] 단위(`MeetingServiceTest` 5건): hostMemberId = 생성자, `Settlement` `DRAFT` 1건, 템플릿 "밥→볼링" → 차수 2개 seq 1·2 모두 `UNDECIDED`, 직접 구성 공백 제거, 비멤버는 저장 없음, 다가오는/지난 분리
  - [x] 단위(`MeetingAccessCheckerTest` 5건): 없는 모임·비멤버 `NOT_GROUP_MEMBER`, 호스트 아님 `NOT_MEETING_HOST`, 통과 시 모임 반환 + `RoundTemplateTest`
  - [x] `@DataJpaTest`(`MeetingRepositoryTest`): 다가오는/지난 경계·정렬·다른 그룹 제외
  - [x] `@WebMvcTest`(`MeetingControllerTest`): 제목·날짜 누락 400 + 필드 에러, 템플릿·차수 둘 다/둘 다 없음/빈 목록 400, 차수 6개·이름 31자·공백 400, 알 수 없는 템플릿 400, 비로그인 401, 비멤버 403, 생성·상세·목록 응답 형식
  - [x] 통합(`MeetingServiceIntegrationTest`): 실제 DB에 모임 1·차수 2·`DRAFT` 정산 1건
- **수락 기준**
  - [x] Given 그룹 멤버 / When 제목·날짜·시간·메모로 모임을 생성하면 / Then 모임이 그룹에 등록되고 모임 상세 링크가 반환되며, 생성자가 `Meeting.hostMemberId`로 저장되고 `Settlement`가 `DRAFT`로 함께 생성된다. 제목·날짜 누락 시 400과 필드 에러를 받는다. (PRD M-1)
  - [x] Given 모임 생성 화면 / When "밥→볼링" 템플릿을 고르면 / Then 1차(밥)·2차(볼링) 차수가 순서대로 생성되고, 각 차수의 장소 상태는 `UNDECIDED`다. (PRD M-2)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 260건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족

### Task 013: 차수별 참석 응답·마감·현황판   ✅ 완료

- **완료일:** 2026-11-03 · 브랜치 `feature/attendance`
- **관련 기능:** M-3, M-4
- **선행 Task:** Task 010, Task 012
- **구현 사항**
  - [x] `AttendanceService`(facade, 트랜잭션 없음) → `AttendanceWriter`(`@Transactional`) — `(round, member)` upsert(기존 행 `changeStatus`, 없으면 생성). 동시 첫 응답 유니크 위반은 새 트랜잭션으로 1회 재시도(`InviteJoinService` 패턴). 요청 `AttendanceRespondRequest`는 단축 옵션 `shortcut` 또는 차수별 `rounds` 중 **정확히 하나**(아니면 400 `answerSpecValid`), 차수별 응답은 일부 차수만 보내도 됨·같은 차수 중복은 400 `roundIdsUnique`, 모임에 없는 차수는 404 `ROUND_NOT_FOUND`
  - [x] 단축 옵션 `AttendanceShortcut`(`LATE_FROM_SECOND`/`FIRST_ONLY`).expand(차수 수) — ⚠️ 결정(2026-11-03): **1차 기준**으로 나눔(늦참 = 1차 불참·2차~끝 참석, 1차만 = 1차 참석·나머지 불참). 차수 1개 모임은 400 `ATTENDANCE_SHORTCUT_NOT_APPLICABLE`
  - [x] 마감 `Meeting.isResponseClosed(now)`(`now > responseDeadline`, 정각은 열림, 마감 없으면 항상 열림). 마감 후 본인 수정은 403 `ATTENDANCE_DEADLINE_PASSED`. ⚠️ 결정(2026-11-03): **호스트는 마감 후에도 자기 응답 포함 수정 가능**. 대리 수정 대상이 그룹 멤버가 아니면 404 `ATTENDANCE_TARGET_NOT_MEMBER`
  - [x] `AttendanceController`: `PUT /api/meetings/{id}/attendances/me`, `PUT /api/meetings/{id}/attendances/{memberId}`(호스트 대리), `GET /api/meetings/{id}/attendance-board`
  - [x] 현황판 `AttendanceBoardService` — 차수·응답·그룹 멤버를 한 번씩 읽어 차수별 참석/불참/미정/미응답(그룹 멤버 − 응답자) 목록(그룹 닉네임·이모지, 가입 순) + `responseDeadline`·`closed`
  - [x] 응답 저장 시 `AttendanceChangedEvent`(`ATTENDANCE_CHANGED`, 본문 = 그 멤버의 차수별 응답 `MemberAttendanceResponse`)를 `/topic/meetings/{id}`로 커밋 후 브로드캐스트
  - [x] `AttendanceRepository.findAllByMeetingId`, `findAllByMeetingIdAndMemberId`
  - [x] 지출 있는 차수의 정산 직렬화·`REOPENED`·재시도 facade 연결은 Task 025에서 완료(5-a 범위)
- **테스트**
  - [x] 단위(`AttendanceShortcutTest` 3건): 차수 2개 늦참 = (불참, 참석)·1차만 = (참석, 불참), 차수 3개, 차수 1개 예외
  - [x] 단위(`MeetingTest` +2건): 마감 없음, 마감 정각 열림·1초 후 닫힘
  - [x] 단위(`AttendanceWriterTest` 10건, 고정 `Clock`): 첫 응답 생성·이벤트, 재응답 시 기존 행 갱신(save 없음), 부분 응답, 마감 1초 후 403, 호스트 본인·대리 수정 마감 후 허용, 비호스트 대리 403, 대상 비멤버 404, 없는 차수 404, 차수 1개 단축 옵션 400
  - [x] 단위(`AttendanceServiceTest` 2건): 유니크 위반 재시도 성공, 재시도도 실패하면 전파
  - [x] 단위(`AttendanceBoardServiceTest`): 상태별 분류, 미응답 = 그룹 멤버 − 응답자(차수마다 다름), 마감 여부
  - [x] `@DataJpaTest`(`AttendanceRepositoryTest` +1건): 모임 단위 조회가 다른 모임 응답 제외, 회원별 조회
  - [x] `@WebMvcTest`(`AttendanceControllerTest`): 단축/차수 둘 다·둘 다 없음·빈 목록 400, 차수 중복 400, status 누락 400, 알 수 없는 단축 옵션 400, 비로그인 401, 마감 403·비호스트 대리 403·비멤버 현황판 403, 응답·현황판 형식
  - [x] 통합(`AttendanceServiceIntegrationTest`): 실제 DB에서 늦참 → 1차만 재응답 시 차수당 1건 유지·값 갱신
- **수락 기준**
  - [x] Given 차수가 2개인 모임 / When 멤버가 "늦참(2차부터)"을 고르면 / Then 1차는 불참, 2차는 참석으로 저장된다. "1차만"은 그 반대로 저장된다. 같은 멤버가 다시 응답하면 기존 응답이 갱신된다(차수당 1건). (PRD M-3)
  - [x] Given 응답 마감 시간이 지난 모임 / When 멤버가 응답을 바꾸려 하면 / Then 거부된다(호스트 = 모임 생성자의 대리 수정은 허용, 그 외 멤버 403). 현황판은 차수별로 참석/불참/미정/미응답 멤버 목록을 보여준다. (PRD M-4)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 293건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족

---

## Phase 4: 장소 결정

- **목표:** 카카오 로컬 실시간 프록시 검색(참고용), 사용자 직접 입력 후보 등록, 바로 확정 / 투표 / 마감 자동 확정, 지도 URL 링크와 OG 노출
- **포함 기능 ID:** P-1, P-2, P-3, P-4, P-5
- **카카오 데이터 무저장 원칙(PRD 오픈 이슈 5 종결, 2026-11-04):** 카카오에 문의하지 않고 허용이 확인되지 않은 활용은 전부 불가로 본다. 검색 결과는 참고용으로 보여주기만 하고, 장소 ID(`kakaoPlaceId`)·이름·주소·좌표·place_url 등 카카오 응답 값은 어디에도 저장하지 않는다. 후보는 사용자가 직접 입력한 `label`(필수)·`manualAddress`·`manualMapUrl`(선택)만 저장한다. 프론트는 검색 결과를 입력칸에 자동으로 채우지 않는다(백엔드가 강제할 수 없는 프론트 규칙). 길찾기 `/link/to`는 제공하지 않는다.

### Task 014: 장소 값 타입·후보·투표 도메인과 지도 링크 생성   ✅ 완료

- **완료일:** 2026-11-04 · 브랜치 `feature/place-candidate`
- **관련 기능:** P-1, P-2, P-3, P-5
- **선행 Task:** Task 012
- **구현 사항**
  - [x] `place/domain/Place` `@Embeddable`(label, manualAddress?, manualMapUrl?) — **모두 사용자 입력**. `kakaoPlaceId`를 포함해 카카오 응답 값 컬럼 없음. ⚠️ 길이는 PRD 미정이라 결정(2026-11-04): **label 50자, manualAddress 200자**, manualMapUrl 2,048자
  - [x] `place/domain/PlaceCandidate`(round, place, proposedBy → `Member`), `PlaceVote`(candidate, member) — `(candidate, member)` 유니크 `uk_place_vote_candidate_id_member_id`. Repository 2종(`JpaRepository`만, 조회 메서드는 016·017에서)
  - [x] `Round.confirmedCandidate`(→ `PlaceCandidate`, nullable, `confirmed_candidate_id`) 컬럼 추가
  - [x] `Round` 장소 상태 전이 메서드: `confirmDirectly(candidate)`(UNDECIDED→CONFIRMED), `openVoting(deadline)`(UNDECIDED→VOTING, CONFIRMED→VOTING), `confirm(candidate)`(VOTING→CONFIRMED). 허용 안 된 전이는 409 `ROUND_PLACE_INVALID_TRANSITION`. ⚠️ 결정(2026-11-04): 재투표 시 `confirmedCandidate`는 비운다(기존 후보·표 유지 여부는 Task 016). 다른 차수 후보로 확정하면 `IllegalArgumentException`(호출 쪽 버그 방어)
  - [x] `place/validation/@HttpsUrl` 검증기 — `https` 스킴 + 호스트 필수, 최대 2,048자, null 통과. 빈 문자열은 실패(요청 DTO의 공백 → null 정규화는 Task 016에서 정함)
  - [x] `place/domain/PlaceLinkFactory`(순수) — 지도 링크 = `manualMapUrl`이 있으면 그것, 없으면 `https://map.kakao.com/link/search/{경로 세그먼트 인코딩(manualAddress ?: label)}`(공백 `%20`, `+`는 `%2B`). `/link/map`·`/link/to`는 장소 ID가 없어 만들지 않음
- **테스트**
  - [x] 단위(`HttpsUrlValidatorTest`): `https://…`·대문자 스킴 통과, `http://…`·`javascript:…`·`ftp://…`·빈 문자열·공백·호스트 없음·스킴 없음·형식 오류 실패, 2,048자 통과·2,049자 실패, null 통과
  - [x] 단위(`PlaceLinkFactoryTest` 4건): manualMapUrl 우선, manualAddress 검색어, label 검색어(주소 null·공백), 한글·공백·예약 문자 인코딩
  - [x] 단위(`RoundTest` 8건): 허용 전이 3종 + 재투표 시 확정 해제, 그 외 전이 `ROUND_PLACE_INVALID_TRANSITION`, 다른 차수 후보 예외
  - [x] `@DataJpaTest`(`PlaceCandidateRepositoryTest` 4건): label만 있는 장소·주소·링크까지 있는 장소 매핑, `confirmedCandidate` FK 저장, `information_schema`로 `place_candidate` 컬럼이 사용자 입력·FK·시각뿐임을 확인 / (`PlaceVoteRepositoryTest` 2건): `(candidate, member)` 유니크, 다른 후보 복수 투표 허용
- **수락 기준**
  - [x] Given 직접 입력 / When `manualMapUrl`이 `http://…`·`javascript:…`이거나 2,048자를 넘으면 / Then 검증에 실패한다(API에서는 400 + `manualMapUrl` 필드 에러, Task 016).
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 355건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족

### Task 015: 카카오 로컬 검색 실시간 프록시   ✅ 완료

- **완료일:** 2026-11-03 · 브랜치 `feature/place-search`
- **관련 기능:** P-1, 6장 외부 API
- **선행 Task:** Task 006
- **구현 사항**
  - [x] `place/client/KakaoLocalClient` — 키워드 검색(`dapi.kakao.com/v2/local/search/keyword.json`, `Authorization: KakaoAK {키}`), 전용 `kakaoLocalRestClient`(`place/config/KakaoLocalConfig`, 연결·읽기 3초). 설정 `place/config/KakaoLocalProperties`(`kakao.local.rest-api-key` — 기본은 `KAKAO_REST_API_KEY`, 없으면 `KAKAO_CLIENT_ID`, `toString` 키 마스킹). 로그엔 상태 코드·예외 타입만(키·검색어 미기록)
  - [x] `PlaceSearchService.search(memberId, query, page)` — **트랜잭션·Repository 없음, 캐싱·저장 없음**. 응답 `PlaceSearchResponse(places, page, isEnd)`, `PlaceSearchItem`(kakaoPlaceId, name, categoryName, address, roadAddress, phone, placeUrl) — 좌표는 지도 URL 링크 방침이라 안 내림. 페이지 크기 15. 2026-11-04 무저장 원칙 채택 후에도 그대로 유지(**참고용 표시 전용**, 어떤 값도 후보로 넘어가 저장되지 않음 — `kakaoPlaceId`는 화면 목록 키 정도로만 쓰임)
  - [x] 서버 측 호출량 제한 `PlaceSearchRateLimiter` — 회원별 메모리 고정 윈도우(주입 `Clock`). ⚠️ PRD 미정이라 결정(2026-11-03): **1분에 30회**(`place.search.rate-limit.max-requests`·`window`). 단일 인스턴스 전제 — 다중 인스턴스 배포 시 공유 저장소로 교체 필요
  - [x] 실패·쿼터 초과(429)·키 오류(401)·5xx·타임아웃 → 503 `PLACE_SEARCH_UNAVAILABLE`, 빈도 초과 → 429 `PLACE_SEARCH_RATE_LIMITED`. ⚠️ 결정(2026-11-03): 직접 입력 폴백 안내 "플래그"는 공통 `ErrorResponse`에 필드를 늘리지 않고 **에러 코드 `PLACE_SEARCH_UNAVAILABLE` 자체**로 한다(프론트가 이 코드면 직접 입력 폼 노출, 메시지도 직접 입력 안내)
  - [x] `GET /api/places/search?query=&page=` (로그인 필요) — query `@NotBlank` 100자, page 1~45(카카오 상한, 기본 1)
- **테스트**
  - [x] 목 서버(`KakaoLocalClientTest`): 성공 매핑·`KakaoAK` 헤더·한글 검색어 인코딩, 400/401/403/429/500/503·타임아웃 → `PLACE_SEARCH_UNAVAILABLE`, 로그에 키·검색어 없음
  - [x] 단위(`PlaceSearchServiceTest`): 같은 검색어 2회 → 카카오 2회 호출(캐시 없음), 빈도 초과 시 카카오 미호출, 매핑·빈 결과. 서비스 의존성이 클라이언트·빈도 제한·설정뿐이라 저장 경로 없음
  - [x] 단위(`PlaceSearchRateLimiterTest`): 한도까지 허용·초과 429, 윈도우 정각 리셋, 회원별 독립 + `KakaoLocalPropertiesTest`(기본값·키 마스킹)
  - [x] `@WebMvcTest`(`PlaceSearchControllerTest`): 응답 형식, 본문·헤더에 REST 키 없음, 빈·공백·누락·101자 검색어 400, page 0·46·문자 400, 비로그인 401, 503·429
- **수락 기준**
  - [x] Given 검색어 "강남 볼링" / When 장소를 검색하면 / Then 서버가 매 요청마다 카카오 로컬 API를 실시간 호출해 결과 목록을 반환하고(클라이언트 응답·네트워크에 REST 키 없음), 검색 결과는 DB·캐시에 저장되지 않는다. Given 카카오 API 실패·쿼터 초과 / When 검색하면 / Then 직접 입력 폴백 안내를 받는다. (PRD P-1)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 324건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족 · [ ] 실제 카카오 호출 수동 확인(카카오 콘솔에서 카카오맵/로컬 API 활성화 필요)

### Task 016: 후보 등록·바로 확정·투표 열기   ✅ 완료

- **완료일:** 2026-11-04 · 브랜치 `feature/place-decision`
- **관련 기능:** P-1, P-2, P-4(재투표)
- **선행 Task:** Task 013, Task 014, Task 015
- **구현 사항**
  - [x] 차수 인가 `MeetingAccessChecker.requireRoundMember`·`requireRoundHost`(통과 시 `Round` 반환) — 결정(2026-11-04): **없는 차수도 403 `NOT_GROUP_MEMBER`**(없는 모임과 같은 방침)
  - [x] 요청 `place/dto/PlaceInput`(label `@NotBlank` 50자, manualAddress 200자, manualMapUrl `@HttpsUrl`) — 사용자 직접 입력 하나뿐, **카카오 값 필드 없음**(본문에 `kakaoPlaceId`를 보내도 무시). 앞뒤 공백 제거, 비어 있는 선택 입력은 `null`로 정규화(폼 빈칸이 `@HttpsUrl` 실패가 되지 않게). 후보 추가·바로 확정이 같은 DTO를 쓴다
  - [x] `PlaceCandidateService.add` — ⚠️ 결정(2026-11-04): **`VOTING` 차수에서만** 그룹 멤버 누구나 추가(`UNDECIDED`·`CONFIRMED`는 409 `ROUND_PLACE_INVALID_TRANSITION`, `Round.requireVoting`). 후보 추가는 `Round`를 바꾸지 않아 락 없음
  - [x] `RoundPlaceService.confirmDirectly`(호스트) — `UNDECIDED`인지 먼저 확인(`Round.requireUndecided`) → 후보 1개짜리 `PlaceCandidate`(제안자 = 호스트) + `CONFIRMED`
  - [x] `RoundPlaceService.openVoting`(호스트) — `VotingOpenRequest.placeVoteDeadline` `@NotNull @Future`. ⚠️ 결정(2026-11-04): **재투표(`CONFIRMED → VOTING`)도 이 API가 맡는다**(Task 018의 `reopenVoting`을 당겨옴) — **후보는 유지, 표는 초기화**(`PlaceVoteRepository.deleteAllByRoundId`, 서브쿼리 벌크 delete), 확정 후보는 해제. 동시 변경은 `Round.version` 낙관적 락으로 늦은 쪽 409 `CONCURRENT_MODIFICATION`
  - [x] `RoundPlaceController`: `POST /api/rounds/{roundId}/candidates`(201 `CandidateResponse`), `POST /api/rounds/{roundId}/place/confirm-direct`, `POST /api/rounds/{roundId}/place/voting`(200 `RoundPlaceResponse`)
  - [x] 응답 `CandidateResponse`(id, label, manualAddress, manualMapUrl, `mapUrl` = `PlaceLinkFactory`, proposedByMemberId), `RoundPlaceResponse`(roundId, placeStatus, placeVoteDeadline, confirmedCandidateId, candidates — 등록 순). 득표는 Task 017에서
  - [x] 변경 후 `PlaceChangedEvent`(`PLACE_CHANGED`, 본문 = `RoundPlaceResponse`)를 `/topic/meetings/{id}`로 커밋 후 브로드캐스트(`RoundPlaceSnapshot`이 응답 조립 + 발행)
  - [x] `PlaceCandidateRepository.findAllByRoundIdOrderById`
- **테스트**
  - [x] 단위(`MeetingAccessCheckerTest` +3): 없는 차수 403, 차수 멤버 통과, 차수 호스트 검사
  - [x] 단위(`PlaceCandidateServiceTest` 4건): VOTING 추가 → 저장·이벤트·mapUrl, UNDECIDED·CONFIRMED 409 저장 없음, 비멤버 403
  - [x] 단위(`RoundPlaceServiceTest` 6건): 바로 확정 → 후보 1건 + `CONFIRMED` + `confirmedCandidate`·이벤트, 비호스트 403 저장 없음, 투표 중 바로 확정 409 저장 없음, 투표 열기 → `VOTING` + 마감·표 삭제 없음, 재투표 → 표 삭제·후보 유지·확정 해제, 투표 중 다시 열기 409
  - [x] 단위(`PlaceInputTest` 4건): 공백 정규화, 빈 지도 링크 통과, 공백 이름 실패, `toPlace`
  - [x] `@WebMvcTest`(`RoundPlaceControllerTest` 17건): `manualMapUrl` http/javascript/2,049자 → 400 `manualMapUrl` 필드 에러(후보 추가·바로 확정), label 누락·공백·51자 400, 주소 201자 400, 마감 누락·과거 400, `kakaoPlaceId` 무시, 비로그인 401, 403·409, 201·200 응답 형식
  - [x] `@DataJpaTest`: `findAllByRoundIdOrderById`(다른 차수 제외·등록 순), `deleteAllByRoundId`(그 차수 표만 삭제)
  - [x] 통합(`RoundPlaceServiceIntegrationTest` 2건): 투표 열기 → 멤버 후보 추가 → 투표 중 바로 확정 409, 바로 확정 → 재투표 시 후보 유지·표 0건
- **수락 기준**
  - [x] When 검색 결과를 참고해 후보를 등록하면 / Then 후보는 사용자가 직접 입력한 이름(필수) + 주소·지도 링크(선택)로만 저장되고, `PlaceCandidate`·`Place`에는 장소 ID를 포함해 카카오 응답 값을 담는 컬럼이 없다. Given 직접 입력 / When `manualMapUrl`이 `http://…`·`javascript:…`이거나 2,048자를 넘으면 / Then 400과 `manualMapUrl` 필드 에러를 받고 후보는 저장되지 않는다. (PRD P-1)
  - [x] Given `UNDECIDED` 차수 / When 호스트가 장소를 바로 지정하면 / Then 후보 1개짜리 `PlaceCandidate`가 생성되고 차수는 `CONFIRMED`가 된다. When 호스트가 투표를 열면 / Then 차수는 `VOTING`이 되고 마감 시각이 필수로 저장되며, 그룹 멤버 누구나 후보를 추가할 수 있다. (PRD P-2)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 393건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족

### Task 017: 후보 투표·실시간 득표 현황   ✅ 완료

- **완료일:** 2026-11-05 · 브랜치 `feature/place-vote`
- **관련 기능:** P-3
- **선행 Task:** Task 016
- **선행 조건:** PRD 오픈 이슈 4(복수 선택 vs 1인 1표), 오픈 이슈 6(불참자 투표 허용) — 착수 전 확인(2026-11-05): **현재 안 유지**(복수 선택, 그룹 멤버 누구나). 이슈 자체는 PRD에 열어 둔다
- **구현 사항**
  - [x] `PlaceVoteService`(facade, 트랜잭션 없음) → `PlaceVoteWriter`(`@Transactional`) — `VOTING` 차수만(아니면 409 `ROUND_PLACE_INVALID_TRANSITION`), 이미 투표했으면 그대로(멱등), 동시 중복은 `(candidate, member)` 유니크 위반을 잡아 새 트랜잭션으로 1회 재시도(`AttendanceService` 패턴). 이 차수의 후보가 아니면 404 `PLACE_CANDIDATE_NOT_FOUND`
  - [x] ⚠️ 결정(2026-11-05): **투표 취소 지원**(복수 선택에서 잘못 누른 표를 고칠 수 있게). 표가 없어도 성공(멱등)
  - [x] ⚠️ 결정(2026-11-05): **마감 시각이 지나면 `VOTING`이어도 투표·취소 거부** — 409 `PLACE_VOTE_DEADLINE_PASSED`, `Round.requireVoteOpen(now)`. 마감 정각은 열림(참석 응답과 같은 경계). 마감 처리 직전이나 동점 대기 중에 결과가 바뀌는 것을 막는다
  - [x] `PlaceVoteController`: `PUT`·`DELETE /api/rounds/{roundId}/candidates/{candidateId}/vote`, `GET /api/rounds/{roundId}/votes` — 모두 `RoundVotesResponse`(후보별 `CandidateVotes`: 득표 수·가입 순 투표자 `BoardMember`, 0표 후보 포함, 공개)
  - [x] `VoteChangedEvent`(`VOTE_CHANGED`, 본문 = `RoundVotesResponse`)를 `/topic/meetings/{id}`로 커밋 후 브로드캐스트. 표가 실제로 바뀐 때만 발행. 결정(2026-11-05): 집계는 **표를 쓴 트랜잭션이 커밋된 뒤 새 읽기 트랜잭션**(`PlaceVoteTally.publish`)에서 읽는다 — REPEATABLE READ에서 쓰기 트랜잭션 안에서 집계하면 동시 표가 빠진 현황이 마지막 메시지로 남을 수 있어서
  - [x] 득표는 `RoundPlaceResponse`에 넣지 않고 별도 API로 둔다. 재투표로 표가 초기화되면 `PLACE_CHANGED`만 나가므로 클라이언트가 `GET /votes`로 다시 읽는다
  - 감수한 한계: 전송 순서가 뒤바뀌는 아주 짧은 구간은 남는다(재연결 시 GET으로 맞춤). 투표는 `Round.version`을 건드리지 않아 마감 처리·재투표와 정확히 겹친 표는 남을 수 있다(재투표 때 초기화). 표 커밋 후 집계 조회가 실패하면 500이지만 재요청은 멱등이다
- **테스트**
  - [x] 단위(`PlaceVoteWriterTest` 8건, 고정 `Clock`): 첫 투표 저장(마감 정각 포함), 재투표 저장 없음, 취소, 표 없는 취소, 비멤버 403, 투표 중 아님 409, 다른 차수 후보 404, 마감 후 투표·취소 409
  - [x] 단위(`PlaceVoteServiceTest` 6건): 변경 시에만 발행, 유니크 위반 재시도 성공·재시도 실패 전파, 취소 발행 여부
  - [x] 단위(`PlaceVoteTallyTest` 4건): 후보별 득표 수·가입 순 투표자·0표 후보, 비멤버 403, 이벤트 페이로드에 득표 수·투표자 포함, 없는 차수 404
  - [x] 단위(`RoundTest` +2건): 마감 정각 통과·1초 후 거부, 투표 중 아님 거부
  - [x] `@DataJpaTest`(`PlaceVoteRepositoryTest` +4건): 차수 단위 표 조회, 표 존재 여부, 표 취소 범위·건수, 차수 소속 후보 조회. 유니크 위반은 기존 테스트로 확인
  - [x] `@WebMvcTest`(`PlaceVoteControllerTest` 9건): 응답 형식, 비로그인 401, 비멤버 403, 404, 409 2종, 숫자 아닌 id 400
  - [x] 통합(`PlaceVoteServiceIntegrationTest` 4건, 실제 커밋): 2곳 투표 → 2표·커밋된 현황 브로드캐스트, 재투표 → 1표 유지·재발행 없음, 취소, 같은 표 동시 2건 → 둘 다 성공·1건 저장
- **수락 기준**
  - [x] Given `VOTING` 차수 / When 멤버가 후보 2곳에 투표하면 / Then 두 표가 저장되고 WebSocket으로 득표 수·투표자 목록이 모든 접속자에게 갱신된다. 같은 후보에 다시 투표하면 표는 1개로 유지된다(유니크 제약). (PRD P-3)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 430건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족

### Task 018: 투표 마감 자동 확정·호스트 수동 확정   ✅ 완료

- **완료일:** 2026-11-07 · 브랜치 `feature/place-vote-close`
- **관련 기능:** P-4
- **선행 Task:** Task 017
- **구현 사항**
  - [x] `PlaceVoteCloser`(`@Scheduled`, 트랜잭션 없음) — `RoundRepository.findVoteClosableIds(now)`로 찾은 차수를 `PlaceVoteCloseProcessor`(`@Transactional`)로 차수별 처리. "지남"은 `Round.requireVoteOpen`과 같은 경계(`now > placeVoteDeadline`, 정각은 열림). 낙관적 락 충돌은 info 로그, 그 외 예외는 error 로그 후 다음 차수로 넘어가며 다음 주기에 다시 조회된다. 주기 `place.vote-closer.cron` 기본 30초, 테스트 프로필은 `-`로 끈다
  - [x] 순수 함수 `place/domain/VoteTally.decide(후보 id 목록, 후보별 득표)` → 단독 최다 / 호스트가 고를 후보 목록. ⚠️ 결정(2026-11-07): **전원 0표는 후보 수와 상관없이 자동 확정하지 않고 호스트 선택 대기**(후보 1개여도 동일). 후보 0개도 대기(고를 목록은 빈 배열)
  - [x] 단독 최다 → `CONFIRMED` + `PLACE_CHANGED`, 그 외 → `VOTING` 유지 + `PlaceDecisionRequiredEvent`(`PLACE_DECISION_REQUIRED`, 본문 `roundId`·`tiedCandidateIds`). 호스트 전용 채널이 없어 `/topic/meetings/{id}`로 보내고 프론트가 호스트에게만 선택 화면을 보여 준다
  - [x] `Round.placeVoteClosedAt`(nullable) 추가 — 호스트 선택 대기 표식. 없으면 동점 차수가 주기마다 다시 조회돼 선택 요청이 반복된다. DB에 있어 재시작 후에도 중복 알림이 없고, `version`이 올라 수동 확정과의 충돌도 감지된다. `Round.markVoteClosed`·`isAwaitingHostDecision`·`isVoteCloseDue`, 조회용 인덱스 `idx_meeting_round_place_status_place_vote_deadline`
  - [x] `RoundPlaceService.confirmManually`(호스트, 마감 전·대기 중 언제든, `VOTING`에서만), `POST /api/rounds/{roundId}/place/confirm`(`PlaceConfirmRequest.candidateId` 필수). 이 차수 후보가 아니면 404 `PLACE_CANDIDATE_NOT_FOUND`. `CONFIRMED`에서 다른 후보로 바꾸려면 재투표를 거친다(409 `ROUND_PLACE_INVALID_TRANSITION`)
  - [x] ⚠️ 결정(2026-11-07): **호스트 선택 대기 중 재투표 허용**(`POST .../place/voting`, 후보 유지·표 초기화·새 마감) — 후보 0개 차수가 멈추지 않게 한다. 마감 전 `VOTING`에서 다시 열기는 그대로 409. 재투표(`CONFIRMED → VOTING`) 자체는 Task 016에서 구현
  - [x] ⚠️ 결정(2026-11-07): **마감 후 후보 추가 거부** — `PlaceCandidateService.add`가 `requireVoteOpen`을 써서 409 `PLACE_VOTE_DEADLINE_PASSED`(표와 같은 경계)
  - [x] `RoundResponse`·`RoundPlaceResponse`에 `hostDecisionRequired` 추가 — 호스트가 접속해 있지 않았어도 모임 상세에서 대기 상태를 알 수 있다. 동점 후보는 `GET /votes`로 본다
  - [x] 동시성: `Round.version` 낙관적 락 — 마감 처리와 수동 확정이 겹치면 늦은 쪽이 실패. 수동 확정이 지면 409 `CONCURRENT_MODIFICATION`(재시도 없음), 마감 처리가 지면 로그만 남긴다. 마감 처리 커밋 후 들어온 수동 확정은 409 `ROUND_PLACE_INVALID_TRANSITION`
  - 감수한 한계: 자동 확정은 마감 후 최대 약 30초 늦을 수 있다(그 사이 표·후보 추가는 막혀 결과가 바뀌지 않음). 투표는 `Round.version`을 건드리지 않아 마감 정각에 커밋이 겹친 표는 집계에서 빠질 수 있다(Task 017과 같은 한계)
- **테스트**
  - [x] 단위(`VoteTallyTest` 7건): 단독 최다, 2개·3개 동점, 전원 0표(후보 1개·여러 개), 후보 0개, 득표 맵에 없는 후보
  - [x] 단위(`PlaceVoteCloseProcessorTest` 7건): 단독 최다 확정·`PLACE_CHANGED`, 동점 유지·선택 요청, 0표·후보 0개, 이미 `CONFIRMED`·이미 대기·마감이 미뤄진 차수는 아무것도 안 함
  - [x] 단위(`PlaceVoteCloserTest` 2건): 전부 처리, 충돌·예외 뒤에도 다음 차수 처리
  - [x] 단위(`RoundPlaceServiceTest` +6, `RoundTest` +4, `PlaceCandidateServiceTest` +1): 수동 확정 성공·대기 중 확정·비호스트 403·없는 후보 404·이미 확정 409, 대기 중 재투표 시 표 삭제, 마감 후 후보 추가 409
  - [x] `@DataJpaTest`(`RoundRepositoryTest` +2): 마감 대상 조회(정각·대기·미래 마감·미정 제외), 인덱스 생성 확인
  - [x] `@WebMvcTest`(`RoundPlaceControllerTest` +7): 응답 형식, `candidateId` 누락 400, 비로그인 401, 403·404·409 2종
  - [x] 통합(`PlaceVoteCloseIntegrationTest` 7건, 실제 커밋): 스케줄러 메서드로 확정·브로드캐스트, 두 번 돌려도 선택 요청 1회, 대기 중 재투표 후 재처리, 커밋 순서를 고정한 충돌 양방향, 마감 후 수동 확정 409, 실제 동시 실행 시 하나만 반영
  - [x] 통합(`PlaceVoteCloserSchedulingTest` 1건): cron을 켜면 `@Scheduled`가 실제로 실행됨
- **수락 기준**
  - [x] Given 마감 시각이 지난 `VOTING` 차수, 최다 득표 후보가 1개 / When 마감 처리가 실행되면 / Then 그 후보로 `CONFIRMED` 된다. 동점이면 `VOTING`을 유지한 채 호스트에게 선택을 요청한다. 마감 처리와 호스트 수동 확정이 동시에 들어오면 하나만 반영되고 다른 요청은 충돌 응답을 받는다. (PRD P-4)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 474건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족
- **참고:** 로컬 DB는 `ddl-auto=update`면 새 컬럼·인덱스가 자동으로 추가된다

### Task 019: 확정 장소 노출·OG 메타 HTML   ✅ 완료

- **완료일:** 2026-11-08 · 브랜치 `feature/place-og`
- **관련 기능:** P-5, 6장 공유(OG 태그)
- **선행 Task:** Task 013, Task 018
- **선행 조건:** PRD 오픈 이슈 1은 열어 둔 채 진행 — 경로를 SPA와 분리해 영향을 없앴다
- **구현 사항**
  - [x] 모임 상세 응답에 차수별 `confirmedPlace`(label, 지도 링크 — `PlaceLinkFactory`) 추가. `RoundRepository.findAllWithConfirmedPlaceByMeetingId`(fetch join)로 N+1 방지
  - [x] `og/controller/OgController`(`@Controller`, Thymeleaf) — `GET /share/meetings/{shareToken}`, `GET /share/invites/{token}`(비로그인)에서 OG 메타(title, description, url)만 담은 HTML + SPA로 이동. 크롤러가 이동하지 않도록 meta refresh 없이 스크립트로 이동하고 `<a>`를 폴백으로 둔다
  - [x] ⚠️ 결정(2026-11-08): **OG는 SPA와 분리된 `/share/**` 경로**. 모임 상세·초대 응답의 `url`이 이 주소를 가리키며(앱 내부 이동은 id 사용), 기존 `meeting.link.base-url`·`group.invite.base-url`은 SPA 이동 대상 전용이다. 서버 주소는 `share.base-url`(`ShareLinkProperties`)
  - [x] ⚠️ 결정(2026-11-08): **공개 링크는 `Meeting.shareToken`으로 식별**(43자 랜덤, unique) — OG 경로가 공개라 순번 id를 쓰면 번호 대입으로 다른 모임의 제목·장소·인원이 보인다
  - [x] OG 설명 문구 생성기 `meeting/domain/MeetingShareText`(순수): "토요일 모임 · 강남 OO식당 · 5/8명 참석", 장소가 없으면 "장소 미정". ⚠️ 결정(2026-11-08): **"5/8명" = 1차 차수 `ATTEND` 수 / 그룹 전체 멤버 수**
  - [x] 카카오톡 공유 문구 `MeetingDetailResponse.shareText`(제목·일시·1차 확정 장소 label·공유 링크)
  - [x] 없거나 만료된 링크는 JSON 대신 404/410 안내 HTML(`og/unavailable`, 이동 없음). 컨트롤러 로컬 `@ExceptionHandler`
  - [x] 토큰 생성을 `global/common/RandomTokens`로 추출(`InviteTokenGenerator`는 위임)
- **테스트**
  - [x] 단위(`MeetingShareTextTest` 5건): 1차 확정 장소 label 포함, 장소 미정 시 문구, 카카오톡 문구, 첫 차수 장소 조회 + `RandomTokensTest`
  - [x] 단위(`OgServiceTest` 4건): 5/8 계산, 장소 미정, 없는 토큰, 초대 메타
  - [x] 단위(`ConfirmedPlaceResponseTest` 4건): 모임 상세의 지도 링크 — manualMapUrl이 있으면 그것, 없으면 `/link/search/{manualAddress 또는 label}`
  - [x] `@DataJpaTest`(`MeetingShareQueriesTest` 4건): 토큰 조회·유니크, fetch 조회(seq 순·미확정 포함), 참석 수
  - [x] `@WebMvcTest`(`OgControllerTest` 6건): OG HTML에 `og:description`·`og:title`·`og:url` 포함, 카카오 API 목이 호출되지 않음, 사용자 입력 이스케이프, 404·410 HTML, 공개 목록 밖 경로 401
  - [x] 통합(`OgShareIntegrationTest` 4건, 실제 DB·시큐리티·템플릿): 비로그인 OG 설명, 순번 id로 접근 불가, 지도 링크 2종
- **수락 기준**
  - [x] Given 1차 장소가 확정된 모임 / When 모임 링크의 OG 메타를 요청하면 / Then 미리보기 설명에 사용자가 입력한 확정 장소 label이 포함되고, 모임 상세에는 `manualMapUrl`이 있으면 그 링크, 없으면 `https://map.kakao.com/link/search/{manualAddress 또는 label}` 지도 링크가 노출된다(카카오 API 호출·저장 없음). (PRD P-5)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 502건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족
- **참고:** 배포 전에 `share.base-url`을 채워야 `og:url`이 절대 주소가 된다. 로컬 DB에 `meeting` 행이 있으면 `ddl-auto=update`의 `share_token`(not null) 컬럼 추가가 실패하거나 unique에 걸릴 수 있어 `meeting` 관련 테이블을 한 번 비운다. 카카오 크롤러 실제 동작은 배포 후 카카오 공유 디버거로 확인한다(코드로 검증 불가)

---

## Phase 5: 정산 기본·최초 확정 (PRD 5-a)

- **목표:** 리비전이 1개만 있어도 되는 모든 것 — 지출·개별 조정, 벌칙 없는 정산 계산·송금 최소화, 정산 직렬화, 최초 확정(리비전 1 스냅샷), 확정 후 금액 영향 변경 시 `REOPENED`, `ChangeLog` 공통 헬퍼, 락 충돌 재시도 facade
- **포함 기능 ID:** S-1, S-2, S-3, S-5, S-8, S-10(최초 확정), S-11(`REOPENED`·`ChangeLog`)
- 이 Phase 직후엔 `REOPENED`에서 재확정할 수 없다(내부 개발 단계 허용, MVP 배포는 Phase 9 이후 — PRD 8장).

### Task 020: 지출·개별 조정·변경 로그 도메인   ✅ 완료

- **완료일:** 2026-11-08 · 브랜치 `feature/expense-domain`
- **관련 기능:** S-1, S-3, S-11
- **선행 Task:** Task 012
- **구현 사항**
  - [x] `settlement/domain/Expense`(round, payer, amount `long`, memo) — 생성 시 1~1억 검증(`INVALID_INPUT`). ⚠️ 메모 길이는 PRD 미정이라 결정(2026-11-08): nullable·100자
  - [x] `settlement/domain/ExpenseShare`(expense, member, amount?, weight?, excluded) — `(expense, member)` 유니크, `Expense.addShare`로만 생성, 고정 금액 1~1억 검증. ⚠️ 고정 금액·가중치·제외를 함께 쓸 때의 계산 의미와 가중치 범위는 PRD 미정이라 조합을 막지 않음(Task 022·023 참고)
  - [x] `settlement/domain/ChangeLog`(meeting, actorId?, changedAt, entityType, entityId, changeType, beforeJson?, afterJson?, settlementStatusAfter) — 수정·삭제 메서드 없음(append-only, `@Immutable`). `actorId`는 스케줄러 변경을 위해 nullable, `entityId`는 삭제된 엔티티 로그를 위해 FK 아님. `ChangeLogEntityType`·`ChangeType` enum은 벌칙·게임 값을 해당 Task에서 추가
  - [x] Repository 3종(`ChangeLogRepository`는 save·조회만 노출)
- **테스트**
  - [x] `@DataJpaTest`(`ExpenseRepositoryTest` 4건): 매핑, 지출 삭제 시 `ExpenseShare` 함께 삭제, 고아 삭제, `(expense, member)` 유니크
  - [x] `@DataJpaTest`(`ChangeLogRepositoryTest` 4건): 시각 순 조회, nullable 필드, 저장 후 변경 미반영, 저장·조회 외 경로 없음
  - [x] 단위(`ExpenseTest` 8건): `Expense` 금액 0·음수·1·100,000,000·100,000,001 경계, 고정 금액 경계, 제외·가중치 조정
- **수락 기준**
  - [x] Given 금액 100,000,001원 / When `Expense`를 만들면 / Then 검증 실패한다. `ChangeLog`에는 수정 경로가 없다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 518건) · [x] `/spring-tools:validate` 통과(이번 변경 진단 0건, 기존 `InviteController` info 1건) · [x] 수락 기준 충족
- **참고:** `(expense, member)` 유니크가 있어 `shares`를 비우고 같은 회원으로 다시 넣으면 Hibernate가 INSERT를 DELETE보다 먼저 실행해 유니크 위반이 난다. Task 022의 조정 API는 기존 행을 고치거나 삭제 후 `flush`해야 한다

### Task 021: 정산 직렬화·`REOPENED` 전이·`ChangeLog` 헬퍼·락 충돌 재시도 facade   ✅ 완료

- **완료일:** 2026-11-09 · 브랜치 `feature/settlement-guard`
- **관련 기능:** S-11, 6장 트랜잭션·동시성(정산 직렬화, 재시도 규칙 4차 m7)
- **선행 Task:** Task 010, Task 020
- **선행 조건:** PRD 오픈 이슈 17(`REOPENED` 판단 기준 — 현재 안 "변경 종류 기준"), 오픈 이슈 16(`ChangeLog` 공개 범위·필드 화이트리스트) — 현재 안 유지 확인(2026-11-08): 변경 종류 기준, 그룹 멤버 전원 조회·영구 보존, 전/후 JSON은 화이트리스트 DTO만
- **구현 사항**
  - [x] `SettlementRepository.findByMeetingIdForUpdate` — `@Lock(OPTIMISTIC_FORCE_INCREMENT)`
  - [x] `settlement/service/SettlementGuard` — `MANDATORY` 전파(트랜잭션 없이 부르면 바로 실패), 정산 행이 없으면 `MEETING_NOT_FOUND`
    - [x] `lockForAmountChange(meetingId)` — 직렬화용 강제 버전 증가 잠금
    - [x] `markAmountChanged(meetingId, actorId)` — `CONFIRMED`면 `REOPENED`(같은 트랜잭션), `REOPENED`·`DRAFT`면 유지. 잠금 조회를 다시 거쳐 호출자가 잠금을 빠뜨려도 직렬화된다. 실제 전이 때만 `SettlementReopenedEvent`(`SETTLEMENT_REOPENED`, 커밋 후 "재확정 필요" 브로드캐스트) — 이미 `REOPENED`면 재발행 없음
    - [x] `lockOnly(meetingId)` — 금액형 게임 취소용(직렬화만, `REOPENED` 없음, 5차 m7)
  - [x] `Settlement` 전이 메서드: `reopen()`(CONFIRMED→REOPENED, REOPENED 유지, DRAFT 유지, 전이 여부 boolean 반환)
  - [x] `settlement/service/ChangeLogRecorder.record(meetingId, actorId, entityType, entityId, changeType, before, after)` — `MANDATORY`로 같은 트랜잭션 insert, 전/후 값은 표시 인터페이스 `ChangeLogSnapshot` 구현 DTO만(구체 DTO는 Task 022부터). 변경 후 정산 상태를 남기므로 `markAmountChanged` 뒤에 호출
  - [x] `global/concurrency/OptimisticRetryExecutor` — 트랜잭션 **바깥** facade에서 최대 2회 재시도(활성 트랜잭션 안 호출은 `IllegalStateException`), `ObjectOptimisticLockingFailureException`만 대상(유니크 위반·`BusinessException` 제외), 소진 시 409 `CONCURRENT_MODIFICATION`
- **테스트**
  - [x] 단위(`SettlementTest` 3건): DRAFT→유지, CONFIRMED→REOPENED, REOPENED→유지
  - [x] 단위(`SettlementGuardTest` 5건·`ChangeLogRecorderTest` 3건): 전이 시에만 이벤트, 정산 없음 `MEETING_NOT_FOUND`, JSON·시각·변경 후 상태·null 처리
  - [x] 통합(`SettlementGuardIntegrationTest` 7건, DB): 같은 모임에 금액 영향 쓰기 2건 동시 → 하나 성공, 다른 쪽 `ObjectOptimisticLockingFailureException`
  - [x] 통합: 변경 트랜잭션 롤백 시 `ChangeLog`도 없음, 커밋 시 같은 트랜잭션에서 1건, 커밋 후에만 이벤트, 충돌 후 재시도가 새 트랜잭션에서 성공, `lockOnly`는 버전만 증가, 트랜잭션 없는 호출 거부
  - [x] 단위(`OptimisticRetryExecutorTest` 7건): 1회 실패 후 성공, 3회 연속 실패 → `CONCURRENT_MODIFICATION`, `DataIntegrityViolationException`·`BusinessException`은 재시도 없이 전파, 활성 트랜잭션 안 호출 거부
- **수락 기준**
  - [x] Given `CONFIRMED` 정산 / When 금액 영향 쓰기가 `markAmountChanged`를 호출하면 / Then 같은 트랜잭션에서 `REOPENED`가 되고 커밋 후에만 "재확정 필요" 이벤트가 나간다.
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 543건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 상태가 바뀌는 쓰기는 dirty flush와 강제 증가가 겹쳐 `version`이 2 오를 수 있어 테스트는 증가 여부만 본다. 강제 증가 충돌은 커밋 시점에 나므로 `@Transactional` 메서드 안에서는 잡을 수 없다(facade에서 처리)

### Task 022: 지출 등록·수정·삭제·개별 조정 API   ✅ 완료

- **완료일:** 2026-11-09 · 브랜치 `feature/expense-api`
- **관련 기능:** S-1, S-3, S-11
- **선행 Task:** Task 021
- **구현 사항**
  - [x] `ExpenseService.create/update/delete/list` — 그룹 멤버 권한, `SettlementGuard.lockForAmountChange` + `markAmountChanged`, `ChangeLogRecorder`(항상 기록, 호출당 1건). 지출 조회·인가는 `ExpenseLoader`로 공유. 재시도 facade는 PRD 6장 재시도 대상이 아니라 붙이지 않음(늦은 쪽 409)
  - 대상 지출 삭제 시 `PenaltyRule` 처리는 Task 037에서 연결(이 시점엔 규칙 없음)
  - [x] `ExpenseShareService.adjust(expenseId, 조정 목록)` — PUT 전체 교체(빈 목록은 조정 해제), 같은 직렬화. 로그는 호출당 1건(`EXPENSE_SHARE`, `entityId` = 지출 id). 같은 회원은 기존 행을 고쳐 유니크 위반을 피함(`Expense.putShare`·`removeSharesExcept`)
  - [x] ⚠️ 결정(2026-11-09): 조정은 **한 사람당 제외/고정 금액/가중치 중 정확히 하나**(아니면 400 `singleAdjustment`), 가중치 **1~100** 정수. 조정 대상은 **그룹 멤버 전체** 허용(참석자 기준 반영은 Task 023 계산에서)
  - [x] 결제자는 그룹 멤버여야 함(아니면 400 `INVALID_INPUT`). ⚠️ 결정(2026-11-09): **차수 불참자도 결제자가 될 수 있음**
  - [x] DTO: `ExpenseRequest`(amount `@NotNull @Min(1) @Max(100_000_000)` `Long`, payerId `@NotNull`, memo 100자 — `Expense` 컬럼과 동일, 공백은 null), `ExpenseShareAdjustRequest`·`ExpenseShareItem`, `ExpenseResponse`·`ExpenseShareResponse`, 로그용 `ExpenseSnapshot`(삭제 로그에 조정 포함)·`ExpenseSharesSnapshot`
  - [x] Controller(`ExpenseController`): `POST /api/rounds/{roundId}/expenses`(201), `PUT/DELETE /api/expenses/{id}`, `PUT /api/expenses/{id}/shares`, `GET /api/meetings/{id}/expenses`
  - [x] 에러 코드: `EXPENSE_NOT_FOUND`(404)
  - [x] (추가) `spring.jackson.deserialization.accept-float-as-int=false` — Jackson 기본값은 `1.5`를 정수 필드에서 1로 잘라 통과시킨다(전역 적용)
- **테스트**
  - [x] `@WebMvcTest`(`ExpenseControllerTest`): 금액 0·음수·소수(`1.5`, `1.0`)·문자열·100,000,001 → 400, 1·100,000,000 → 성공, 조정 조합·범위·회원 중복 400, 비로그인 401
  - [x] 단위(`ExpenseServiceTest`·`ExpenseShareServiceTest`·`ExpenseLoaderTest`): 생성·수정·삭제·조정 시 `ChangeLog` 1건(전/후 값), `markAmountChanged` 호출 순서, 비멤버 403 `NOT_GROUP_MEMBER`, 없는 지출 404, 비그룹 결제자·조정 대상 400
  - [x] `ExpenseTest`·`ExpenseRepositoryTest` 추가: 수정·`putShare`·`removeSharesExcept`, 목록 정렬·다른 모임 제외
  - [x] 통합(`ExpenseServiceIntegrationTest` 7건, DB): `CONFIRMED` → `REOPENED` + `ChangeLog` + 커밋 후 이벤트 1회, 조정 재요청 유니크 위반 없음, 삭제 시 조정 함께 삭제, `DRAFT` 유지
- **수락 기준**
  - [x] Given 2차 차수 / When 금액 100,000원·결제자 철수로 지출을 등록하면 / Then 지출이 저장된다. 금액이 0 이하이거나 정수가 아니거나 1억 원을 넘으면 400을 받는다. 정산이 `CONFIRMED`면 지출 추가·수정·삭제 모두 처리되고 같은 트랜잭션에서 `Settlement`가 `REOPENED`가 된다. (PRD S-1 중 벌칙 무관 부분)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 615건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 고정 금액 합이 지출 금액을 넘는 경우는 막지 않았다. 지출 금액 수정으로도 생길 수 있어 Task 023의 조정 계산 규칙과 함께 정한다

### Task 023: 정산 계산 순수 도메인(벌칙 제외)·송금 최소화   ✅ 완료

- **완료일:** 2026-11-09 · 브랜치 `feature/settlement-calc`
- **관련 기능:** S-2, S-3, S-5, S-8
- **선행 Task:** Task 020
- **선행 조건:** PRD 오픈 이슈 8 — 아래 결정(2026-11-09)으로 진행. 결제자가 여럿인 경우는 `Expense`에 결제자가 1명이라 해당 없음
- **구현 사항**
  - [x] `settlement/calc/` 패키지 — JPA·Spring 의존 없음, 불변식 위반은 `IllegalStateException`
  - [x] 입력 모델 `SettlementInput`·`ExpenseInput`·`ShareAdjustment`, 출력 `SettlementResult`(`MemberSettlement` 멤버별 부담액·순잔액, `TransferGuide` 송금 안내, `SettlementWarning` 경고)
  - [x] `ShareDistributor` — 고정 금액을 먼저 떼고 남은 금액을 가중치대로 나눠 100원 단위 올림, 초과분 차감. 제외자 0원
  - [x] ⚠️ 결정(2026-11-09, PRD 미정 항목)
    - 분배 대상은 차수 `ATTEND`만. 비참석자에게 걸린 조정(제외·가중치·고정 금액)은 전부 무시하고, 저장된 조정은 참석으로 바뀌면 다시 적용
    - 가중치: 조정 없는 사람은 1, 1인 = `ceil(금액 × w ÷ Σw)`의 100원 올림(전원 1이면 N빵과 같음)
    - 초과분(오픈 이슈 8): ① 결제자가 풀(제외·고정 금액이 아닌 분배 대상)에 있으면 그 몫에서 먼저 ② 남으면 풀에서 `memberId` 내림차순, 각 몫 0원 하한(D3와 같은 방식)
    - 고정 금액은 올림 없이 그대로. 합이 지출을 넘으면 `memberId` 오름차순으로 남은 금액까지만 부담 + 경고 `FIXED_AMOUNT_OVERFLOW`
    - 나눌 사람이 없는 잔액은 결제자가 부담(불참 결제자 포함) + 경고 `NO_SHARE_TARGET`
  - [x] `NetBalanceCalculator` — 결제액 − 부담액, 합 0 검증
  - [x] `TransferMinimizer` — 가장 큰 채권자·채무자를 반복 매칭(동률은 `memberId` 오름차순), 건수 ≤ 0 아닌 인원 − 1, 결정적
  - [x] `SettlementCalculator` — 분배 → 순잔액 → 송금 안내 진입점(Task 035에서 확장)
  - [x] 모든 연산 `long`, 곱셈 `Math.multiplyExact`
- **테스트**
  - [x] S-2: 2차 참석 4명, 100,000원, 벌칙 없음 → 4명 25,000원, 불참자 없음
  - [x] S-3: 4명 중 지수 제외 → 지수 0원, 3명 분배(100,000 ÷ 3 = 33,333.3… → 33,400원, 초과분 200원 결제자 차감 → 33,200원, 합 100,000원)
  - [x] S-8 경계: 나누어떨어짐(초과분 0), 1원 차이, 100원 미만 금액(50원 ÷ 3 → 0 / 50 / 0), 1억 원
  - [x] S-5: 예 1의 순잔액(철수 +76,800 / 민수 −30,000 / 영희 −23,400 / 지수 −23,400) → 3건, 합 76,800원, 송금 후 모두 0
  - [x] S-5: 여러 채권자·채무자 혼합에서 건수 ≤ 0 아닌 인원 − 1, 결과 결정적
  - [x] 순잔액 합 0 불변식 검증(여러 지출·여러 결제자, 고정 시드 무작위 입력 200회)
  - [x] 결정 사항: 불참 결제자, 가중치 올림, 고정 금액 초과·결제자 고정, 나눌 사람 없음, 비참석자 조정 무시
- **수락 기준**
  - [x] Given 2차 참석자 4명, 지출 100,000원, 벌칙 없음 / When 정산을 계산하면 / Then 4명 모두 25,000원이 배정되고 불참자는 분배 대상에 없다. (PRD S-2)
  - [x] Given 참석자 4명 중 지수를 제외 / When 정산을 계산하면 / Then 지수 부담액은 0원이고 나머지 3명이 나눈다(S-8 적용). 가중치(비율) 조정 시 가중치에 비례해 배정된다. (PRD S-3)
  - [x] Given 예 1의 순잔액 / When 송금 안내 목록을 생성하면 / Then 송금 건수 3건, 금액 합 76,800원인 목록이 나오고 모든 멤버의 순잔액이 송금 후 0이 된다. 송금 건수는 (순잔액이 0이 아닌 인원 − 1) 이하다. (PRD S-5)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 842건, 무작위 불변식 반복 200회 포함) · [x] `/spring-tools:validate` 통과 · [x] 수락 기준 충족
- **참고:** `ATTEND` 필터링은 Task 024의 `SettlementInputAssembler` 몫이다. 경고는 계산을 막지 않고 결과에 담기기만 하므로 표시·확정 처리도 Task 024에서 정한다. 송금 건수는 상한만 보장하고 수학적 최소는 아니다(PRD 수용 기준이 상한)

### Task 024: 정산 조회·최초 확정·리비전 1 스냅샷   ✅ 완료

- **완료일:** 2026-11-09 · 브랜치 `feature/settlement-confirm`
- **관련 기능:** S-5, S-10(최초 확정), S-11
- **선행 Task:** Task 022, Task 023
- **구현 사항**
  - [x] 엔티티(모두 `@Immutable`, 양방향 컬렉션 없이 각자 Repository로 저장): `SettlementRevision`(settlement, revisionNo, confirmedBy, confirmedAt, warningsAcknowledged) — `(settlement, revisionNo)` 유니크, 수정 메서드 없음 / `RevisionMemberShare`(revision, member, shareAmount, netBalance) — `(revision, member)` 유니크 / `Transfer`(revision, fromMember, toMember, amount) — 송금 상태 필드 없음(E1)
  - [x] `SettlementQueryService.get(meetingId, memberId)` — `basisRevisionNo`: `CONFIRMED`·`REOPENED` = `currentRevisionNo` 기준 저장된 `Transfer`, `DRAFT` = null + 현재 계산값 "미리보기"(저장 안 함). 그룹 멤버 권한
  - [x] `SettlementConfirmService.confirm(meetingId, hostId, acknowledgeWarnings)` — 호스트만(403 `NOT_MEETING_HOST`), `lockForAmountChange`, `DRAFT → CONFIRMED`, 리비전 1 + 멤버별 스냅샷 + `Transfer`, `firstConfirmedAt`·`currentRevisionNo` 기록. `CONFIRMED`면 409 `SETTLEMENT_ALREADY_CONFIRMED`. `REOPENED` 재확정은 Task 039
  - [x] 정산 입력 조립기 `SettlementInputAssembler`(DB → `SettlementInput`, 차수별 `ATTEND`만) — 이후 벌칙 입력 확장 자리
  - [x] Controller(`SettlementController`): `GET /api/meetings/{id}/settlement`, `POST /api/meetings/{id}/settlement/confirm`(200)
  - [x] 에러 코드: `SETTLEMENT_ALREADY_CONFIRMED`(409), `WARNING_UNCONFIRMED`(409)
  - [x] ⚠️ 결정(2026-11-09, PRD·ROADMAP 미정 항목)
    - 계산 경고(`FIXED_AMOUNT_OVERFLOW`·`NO_SHARE_TARGET`)가 있는데 `acknowledgeWarnings`가 true가 아니면 409 `WARNING_UNCONFIRMED`. 에러 응답에 경고 목록을 싣는 건 Task 038, 지금은 `GET` 응답의 `warnings`로 본다
    - `REOPENED`에서 확정 요청도 Task 039 전까지 409 `SETTLEMENT_ALREADY_CONFIRMED`
    - 지출 0건 모임도 확정 가능(스냅샷·`Transfer` 0건인 리비전 1)
    - `warningsAcknowledged`는 경고가 실제로 있었고 확인한 경우만 true
    - `warnings`는 `DRAFT` 미리보기에서만 채움(리비전에 경고 내용을 저장하지 않음). `REOPENED`의 현재 경고 표시는 Task 039에서
    - 요청 본문은 생략 가능, 본문·필드가 없으면 미확인(false)
    - 최초 확정은 `ChangeLog`·실시간 이벤트 없음(PRD 기록·알림 대상 아님)
- **테스트**
  - [x] 단위(`SettlementConfirmServiceTest` 9건): 리비전 1·`RevisionMemberShare`·`Transfer`가 계산값대로 저장, `firstConfirmedAt` 기록, `CONFIRMED`·`REOPENED` 재요청 409, 비호스트 403, 경고 미확인 409(`DRAFT` 유지), 경고 확인 기록, 지출 0건, 정산 flush 후 리비전 저장 순서
  - [x] 단위: `SettlementTest` 3건 추가, `SettlementInputAssemblerTest` 3건, `SettlementQueryServiceTest` 7건
  - [x] `@DataJpaTest`(`SettlementRevisionRepositoryTest` 5건): `(settlement, revisionNo)`·`(revision, member)` 유니크, 조회 순서
  - [x] `@WebMvcTest`(`SettlementControllerTest` 12건): `DRAFT` 조회 시 `basisRevisionNo = null`·미리보기 표시, `CONFIRMED` 조회 시 `basisRevisionNo = 1`, 본문 생략·필드 값별 전달, 에러 코드별 응답, 비로그인 401
  - [x] 통합(`SettlementConfirmIntegrationTest` 3건, DB): 확정 후 지출 수정 → `REOPENED`이고 조회는 리비전 1 금액 유지, 동시 확정 2건 → 1건만 성공, 확정 계산 뒤 지출 수정이 먼저 커밋 → 확정 충돌·`DRAFT` 유지
- **수락 기준**
  - [x] Given `DRAFT` 정산 / When 호스트가 확정하면 / Then `Settlement`가 `CONFIRMED`가 되고 `SettlementRevision`(리비전 1, 확정자·시각, 멤버별 부담액·순잔액)과 그 리비전의 송금 안내 목록(`Transfer`: from·to·amount)이 그 시점 계산값으로 저장된다. `CONFIRMED` 상태에서 다시 요청하면 409(`SETTLEMENT_ALREADY_CONFIRMED`). (PRD S-10 중 벌칙 무관 부분)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 884건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 리비전 INSERT의 FK 검사가 정산 행에 공유 잠금을 잡아서, 커밋 때 정산을 UPDATE하면 동시 확정끼리 MySQL 데드락(500)이 난다(통합 테스트로 재현). 그래서 `confirmFirst` 뒤 `settlementRepository.flush()`로 정산 UPDATE를 리비전 INSERT보다 먼저 내보내 늦은 쪽이 409 `CONCURRENT_MODIFICATION`으로 끝나게 했다. Task 039의 재확정도 같은 순서를 지켜야 한다

### Task 025: 확정 후 금액 영향 변경의 `REOPENED` 연결(참석 포함)   ✅ 완료

- **완료일:** 2026-11-10 · 브랜치 `feature/settlement-reopen`
- **관련 기능:** S-11, M-3, 6장 정산 직렬화·재시도
- **선행 Task:** Task 013, Task 024
- **선행 조건:** PRD 오픈 이슈 17(현재 안 유지 확인)
- **구현 사항**
  - [x] `settlement/service/AttendanceSettlementHandler`(`MANDATORY`) — `AttendanceWriter`가 실제로 바뀐 응답을 넘기면, 지출 1건 이상인 차수만 추려 `lockForAmountChange` + `markAmountChanged`, 지출 없는 차수는 직렬화 제외(`ExpenseRepository.findRoundIdsWithExpense`)
  - [x] 참석 변경 `ChangeLog`는 정산이 한 번이라도 확정된 뒤(`Settlement.hasBeenConfirmed`, `currentRevisionNo ≥ 1`)부터만 기록. 행위자는 본인 응답이면 본인, 대리 수정이면 호스트
  - [x] 참석 응답 Controller → `AttendanceService`(`OptimisticRetryExecutor` facade, 기존 유니크 위반 1회 재시도를 안에 유지) → `AttendanceWriter`(재시도 시 같은 응답이 이미 저장됐으면 쓰기 없이 성공, 멱등)
  - [x] `ExpenseShare`·지출 변경 경로 점검: `ExpenseService.create/update/delete`, `ExpenseShareService.adjust`뿐이고 모두 `markAmountChanged`를 거침
  - [x] "재확정 필요" 이벤트 페이로드: `SettlementReopenedResponse(actorId, changes)`, `SettlementChangeSummary(entityType, entityId, changeType)` — `markAmountChanged`에 요약 인자 추가
  - [x] ⚠️ 결정(2026-11-10, PRD·ROADMAP 미정 항목)
    - "참석 변경"은 값이 실제로 달라진 차수만. 첫 응답과 `ABSENT ↔ UNDECIDED`도 포함(PRD 4.5·오픈 이슈 17의 변경 종류 기준)
    - 같은 값 재응답은 쓰기 없이 성공: `respondedAt` 갱신도 `ATTENDANCE_CHANGED` 이벤트도 없음(Task 013의 동작을 바꿈)
    - 참석 `ChangeLog`는 바뀐 차수(지출 있는 차수)당 1건, `entityId` = 참석 행 id, 첫 응답 `CREATE`·그 외 `UPDATE`
    - 이벤트 요약에 전/후 값은 넣지 않음(상세는 변경 로그 조회)
- **테스트**
  - [x] 단위(`AttendanceSettlementHandlerTest` 5건): 지출 있는 차수 참석 변경 → 잠금·`markAmountChanged`·`ChangeLog` 1건 순서, 지출 없는 차수 → `SettlementGuard` 호출 없음, 확정 전 → `ChangeLog` 없음, 혼합 시 지출 있는 차수만, 첫 응답 `CREATE`
  - [x] 단위(`AttendanceWriterTest` +3건·`AttendanceServiceTest` +2건): 같은 값 재응답 멱등, 바뀐 차수만 전달, 대리 수정 행위자 = 호스트, 낙관적 락 1회 충돌 후 성공, 3회 연속 `CONCURRENT_MODIFICATION`
  - [x] 단위: `SettlementTest` +1건, `@DataJpaTest`: `ExpenseRepositoryTest` +1건
  - [x] 통합(`AttendanceServiceIntegrationTest` 5건, DB): 확정 후 참석 변경 → `REOPENED`·`ChangeLog`·커밋 후 이벤트 1회, 지출 없는 차수·같은 값 재응답 → 상태·버전 불변, 다른 멤버 응답이 먼저 커밋돼 충돌해도 재시도로 둘 다 저장
  - [x] 통합(`SettlementConfirmIntegrationTest` +1건, DB): 리비전 1 확정 → 지출 금액 수정 → `REOPENED`, `ChangeLog`(행위자, 시각, `Expense`, `UPDATE`, 전/후 JSON), 커밋 후 "정산 재확정 필요" 이벤트 1회
- **수락 기준**
  - [x] Given 리비전 1로 확정된 정산 / When 멤버가 지출 금액을 수정하면 / Then 수정은 성공하고 같은 트랜잭션에서 `Settlement`가 `REOPENED`, `ChangeLog`에 (행위자, 시각, `Expense`, `UPDATE`, 전/후 JSON)이 저장되며, 커밋 후 모임 구독자에게 "정산 재확정 필요" 이벤트가 브로드캐스트된다. (PRD S-11 앞부분)
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 901건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** `OptimisticRetryExecutor`는 활성 트랜잭션 안에서 호출을 거부하므로 `AttendanceService`를 부르는 통합 테스트에는 `@Transactional`을 붙일 수 없다. 지출 없는 차수의 참석 변경은 PRD대로 직렬화에서 빠지므로, 그 차수의 첫 지출 등록과 재확정이 동시에 겹치는 극히 좁은 경합은 막지 않는다

---

## Phase 6: 게임 방·사다리·꽝뽑기

- **목표:** 서버가 관리하는 게임 상태 머신(`WAITING → PLAYING → REVEALED → CLOSED`, `→ CANCELED`), 시작 즉시 서버 추첨(사다리·꽝뽑기는 연출 차이), 공정성(공개 전 결과 비노출), 재접속 복구
- **포함 기능 ID:** R-0, R-1, R-2, R-4(벌칙 종류·안내 문구), R-8, R-10

### Task 026: 게임 도메인 골격과 상태 전이 규칙   ✅ 완료

- **완료일:** 2026-11-10 · 브랜치 `feature/game-domain`
- **관련 기능:** R-0, R-1, R-2, R-10, 4.4 게임 상태 전이 표
- **선행 Task:** Task 013, Task 021
- **구현 사항**
  - [x] `game/domain/GameSession`(round, type `LADDER/DRAW/ROULETTE`, status, createdBy, penaltyKind `MONEY/ACTION/NONE`, penaltyNote ≤100자, seed·`resultData`(응답 DTO에 매핑 안 함), revealedAt?, applyOrderAt? `DATETIME(6)`, rootSessionId, rerunOf?(유니크, NULL 허용), canceledRerunOf?, timeLimitSeconds?, `@Version version`). `markAsRoot()` — id가 IDENTITY라 `rootSessionId`는 nullable, Task 027이 저장 직후 호출
  - [x] `GameParticipant`(gameSession, member, ready, pickedSlot?) — `(gameSession, member)` 유니크, `(gameSession, pickedSlot)` 유니크
  - [x] `GameResult`(gameSession, member, outcome)
  - [x] 전이 메서드(현재 상태 위반 시 409 `GAME_INVALID_TRANSITION`): `start()`, `reveal(now)`(revealedAt, 첫 공개면 applyOrderAt, 마이크로초로 절삭), `close()`, `cancel()`(WAITING/PLAYING만, 아니면 409 `GAME_NOT_CANCELABLE`)
  - [x] `isOperator(memberId, meeting)` = `createdBy` 또는 호스트
  - [x] `GameSessionRepository.findByIdForPick` — `@Lock(PESSIMISTIC_WRITE)`, `findGroupIdById`
  - [x] 게임 토픽 해석기 `game/service/GameTopicGroupResolver`(`/topic/games/{gameId}` → 게임 → 차수 → 모임의 groupId, 없는 게임은 null) — Task 010
  - [x] 에러 코드: `GAME_INVALID_TRANSITION`, `GAME_NOT_READY`, `GAME_NOT_CANCELABLE`, `SLOT_ALREADY_PICKED`(모두 409), `NOT_GAME_OPERATOR`(403), `GAME_NOT_FOUND`(404)
  - [x] ⚠️ 결정(2026-11-10, PRD·ROADMAP 미정 항목)
    - 게임 타입은 `LADDER/DRAW/ROULETTE`. 게임은 벌칙자를 정하는 용도라 `BOWLING`은 넣지 않고, `GameResult`의 `rank/score`도 만들지 않는다. 다른 게임은 필요할 때 추가한다(PRD·Task 047은 미수정)
    - `seed`는 `Long`, 결과 배열은 게임별 구조가 달라 TEXT JSON 문자열(`resultData`). 기록은 Task 028·029
    - 제한 시간은 `timeLimitSeconds`. 스케줄러 조회용 시작·마감 시각 컬럼은 Task 030에서 추가
    - `outcome`은 50자 문자열. 어떤 결과가 벌칙자인지 Task 028·032에서 정할 때까지 enum으로 고정하지 않는다
- **테스트**
  - [x] 단위(`GameSessionTest` 26건, 전이 표 전체): 허용 전이 5종 성공, 비허용 전이 → `GAME_INVALID_TRANSITION`, REVEALED/CLOSED/CANCELED cancel → `GAME_NOT_CANCELABLE`, CLOSED·CANCELED에서 어떤 전이도 불가, `isOperator`, `markAsRoot`
  - [x] 단위: 첫 공개만 applyOrderAt 기록(마이크로초 정밀도 유지)
  - [x] 단위(`GameTopicGroupResolverTest` 10건): supports, 해석, 없는 게임, 형식 오류 7종
  - [x] `@DataJpaTest`(`GameSessionRepositoryTest` 7건, `GameParticipantRepositoryTest` 6건): `rerunOf` 유니크(NULL 여러 건 허용), `(gameSession, pickedSlot)`·`(gameSession, member)` 유니크, `applyOrderAt` 마이크로초 저장·조회와 컬럼 정밀도, `findByIdForPick` 잠금, 그룹 id 해석, 결과 저장
- **수락 기준**
  - [x] Given `WAITING`이 아닌 게임 / When 시작하면 / Then 409(`GAME_INVALID_TRANSITION`)이고 상태는 그대로다. — `GameSessionTest.startRejected`
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 950건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족

### Task 027: 게임 방 생성·즉시 추첨·종료·취소 API   ✅ 완료

- **완료일:** 2026-11-10 · 브랜치 `feature/game-lobby`
- **관련 기능:** R-0, R-1, R-2, R-4(벌칙 종류·안내 문구), R-10
- **선행 Task:** Task 026
- **구현 사항**
  - [x] ⚠️ 결정(2026-11-10, PRD R-0·R-1·R-2·R-8과 결정 사항에 반영)
    - 만드는 사람이 **그 차수 `ATTEND` 회원 중 일부 또는 전부**를 골라 방을 만든다(2~50명, 진행자도 하려면 목록에 넣는다). 입장(`join`)·준비(`ready`) 단계는 없다. 참가자는 생성 시점에 확정되고, 이후 참석 변경으로 자동 제외되지 않는다
    - **시작하면 그 자리에서 서버가 벌칙자를 뽑아 공개**한다(`WAITING → PLAYING → REVEALED`를 한 트랜잭션에서). 사다리·꽝뽑기는 화면 연출만 다르고 카드 선택·결과 공개 요청·제한 시간 자동 배정은 없다
    - 권한은 PRD대로 생성은 그룹 멤버 누구나, 시작·종료·취소는 진행자 또는 호스트
    - 벌칙자 수 `penaltyCount`는 선택 입력(기본 1), 1 이상 참가자 수 미만
    - 게임 취소 `ChangeLog`는 **금액형만** 기록한다. 행동형·`NONE` 취소 이력은 `CANCELED` 상태로 남는다
  - [x] `GameLobbyService`: `create`(`rootSessionId` = 자기 id, `PenaltyRule`·`Penalty` 생성 없음, `ROULETTE`는 400 `GAME_TYPE_NOT_SUPPORTED`), `start`, `close`(금액 영향 없음), `cancel`(금액형이면 `SettlementGuard.lockOnly`)
  - [x] `GameDrawer`(순수 함수, memberId 정렬 후 seed로 섞음) + `GameSeedGenerator`(`SecureRandom`, JS 안전 정수 범위). 결과는 `GameResult`의 `PENALTY`/`SAFE`로 전원 저장, `seed`도 저장해 재현 가능
  - [x] 동시 시작은 `GameSession.version`으로 1건만 성공. 결과 insert의 FK 공유 잠금과 데드락이 나지 않도록 상태 변경을 먼저 flush한다
  - [x] 커밋 후 `GAME_STATE_CHANGED` 브로드캐스트: 시작·종료·취소는 게임·모임 토픽 양쪽, 생성은 모임 토픽
  - [x] `global/auth/GameAccessChecker.requireOperator`(없는 게임도 403 `NOT_GROUP_MEMBER`)
  - [x] DTO: `GameCreateRequest`, `GameStateResponse`(`result`는 공개 전 null), `GameParticipantResponse`, `GameResultResponse`(seed, 벌칙자 id), `GameChangeSnapshot`
  - [x] Controller: `POST /api/rounds/{roundId}/games`(201), `POST /api/games/{gameId}/start|close|cancel`
  - [x] 에러 코드 추가: `GAME_PARTICIPANT_NOT_ATTENDING`(400), `GAME_TYPE_NOT_SUPPORTED`(400)
  - [x] 결정으로 쓸모없어진 코드 제거: `GameParticipant.ready`·`pickedSlot`, `GameSession.timeLimitSeconds`, `findByIdForPick`, `GAME_NOT_READY`, `SLOT_ALREADY_PICKED`
- **테스트**
  - [x] 단위(`GameDrawerTest`): 벌칙자 수, 고정 seed 재현, 입력 순서 무관
  - [x] 단위(`GameLobbyServiceTest`): 생성·미참석자 거부·룰렛 거부, 시작(결과 저장·공개·브로드캐스트), 권한 403, 재시작 409, 종료(정산 무변화), 취소(금액형만 직렬화·`ChangeLog`, 공개 후 409)
  - [x] `@DataJpaTest`: 참가자·결과 조회
  - [x] `@WebMvcTest`(`GameControllerTest`): 응답 형식, 공개 전 결과 없음과 안내 문구 포함, 입력 검증 400, 권한 403, 비로그인 401
  - [x] 통합(`GameLobbyIntegrationTest`): 일부 참석자로 생성 → 시작과 양쪽 토픽 전송, 금액형 취소 시 정산 version 증가·`REOPENED` 아님, 동시 시작 시 추첨 1회, 참석자 조회(불참·다른 차수 제외)
- **수락 기준**
  - [x] Given 차수 참석자 중 고른 회원으로 만든 `WAITING` 게임 / When 진행자 또는 호스트가 시작하면 / Then 벌칙자가 정해져 `REVEALED`가 되고 결과가 브로드캐스트된다. 그 외 멤버는 403(`NOT_GAME_OPERATOR`), `WAITING`이 아니면 409(`GAME_INVALID_TRANSITION`)이고 상태는 그대로다.
  - [x] Given `REVEALED` 게임 / When 진행자 또는 호스트가 종료하면 / Then `CLOSED`가 되어 브로드캐스트되고 정산 상태는 바뀌지 않는다. 그 외 멤버면 403, `REVEALED`가 아니면 409.
  - [x] Given 게임 생성 / When 금액형과 안내 문구를 입력하면 / Then `penaltyKind = MONEY`, `penaltyNote`가 저장되고 `PenaltyRule`·`Penalty`는 생성되지 않는다.
  - [x] Given `WAITING` 게임 / When 진행자 또는 호스트가 취소하면 / Then `CANCELED`가 되어 브로드캐스트된다. 그 외 멤버면 403, `REVEALED`/`CLOSED`면 409(`GAME_NOT_CANCELABLE`).
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 982건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 로컬 DB는 `ddl-auto=update`라 제거한 `game_participant.ready`(NOT NULL) 컬럼이 남아 insert가 실패한다 → 로컬에서 `game_participant` 테이블을 한 번 drop 후 실행. `Penalty` 생성과 정산 연동은 Task 032에서 한다

### Task 028: 사다리 타기   ✅ 완료(Task 027로 흡수)

- **관련 기능:** R-1
- 시작 즉시 공개로 결정(2026-11-10)되어 별도 연결 생성·공개 요청이 없다. 공개 응답의 `seed`와 벌칙자 목록으로 프론트가 같은 연출을 재생한다

### Task 029: 꽝뽑기   ✅ 완료(Task 027로 흡수)

- **관련 기능:** R-2
- 시작 즉시 공개로 결정(2026-11-10)되어 카드 선택·동시 공개가 없다. 종류 차이는 화면 연출뿐이다

### Task 030: 재접속 상태 복구·게임 목록   ✅ 완료

- **완료일:** 2026-11-11 · 브랜치 `feature/game-state-recovery`
- **관련 기능:** R-8(재접속 상태 복구만, 제한 시간 자동 배정은 2026-11-10 결정으로 폐지)
- **선행 Task:** Task 027
- **구현 사항**
  - [x] `GET /api/games/{gameId}/state` — 현재 상태, 공개 후엔 결과(seed·벌칙자) 포함(그룹 멤버). `GameQueryService.getState`(읽기 전용), 응답은 실시간 메시지와 같은 `GameStateResponse`라 결과 비노출 규칙도 `GameStateAssembler` 한 곳에서 지킨다
  - [x] `GET /api/rounds/{roundId}/games` — 차수별 게임 목록. 모임 화면을 안 열어둬 실시간 알림을 놓친 참가자가 결과를 찾는 경로
  - [x] `GameAccessChecker.requireMember(gameId, memberId)` — 진행자가 아니어도 그룹 멤버면 통과, 없는 게임도 403 `NOT_GROUP_MEMBER`
  - [x] `GameSessionRepository.findAllByRoundIdOrderByIdDesc`
  - [x] ⚠️ 결정(2026-11-11, ROADMAP 미정 항목)
    - 목록 항목은 요약 DTO를 따로 두지 않고 `GameStateResponse` 그대로 쓴다(참가자·공개된 결과 포함). 목록만으로 결과를 볼 수 있어 추가 호출이 없다
    - 정렬은 **최근에 만든 순**, `CANCELED`·`CLOSED` 게임도 포함한다(걸러 보기는 프론트 몫). 페이지 나눔 없음
    - 한 차수의 게임은 몇 건뿐이라 게임마다 참가자·결과를 따로 읽는다(일괄 조회 최적화 안 함)
- **테스트**
  - [x] `@WebMvcTest`(`GameControllerTest` 6건 추가): 공개 전 응답에 결과·seed 없음, 공개 후 결과 포함, 비멤버 403, 목록 응답, 비로그인 401
  - [x] 단위(`GameQueryServiceTest` 4건, `GameAccessCheckerTest` 3건): 인가 실패 시 조회·조립 안 함, 목록 순서 유지, 없는 게임 존재 숨김
  - [x] `@DataJpaTest`(`GameSessionRepositoryTest` 1건 추가): 최근 순, 다른 차수 제외
  - [x] 통합(`GameLobbyIntegrationTest` 3건 추가): 게임 참가자가 아닌 그룹 멤버가 공개 전·후 상태 복구, 목록(대기·취소·공개 혼합), 그룹 밖 회원 거부
- **수락 기준**
  - [x] Given 연결이 끊겼던 참가자 / When 재접속하면 / Then REST로 현재 게임 상태와(공개 후라면) 벌칙자를 받아 화면을 복구한다. — `GameLobbyIntegrationTest.recoverStateAfterReconnect`
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 999건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** `GameControllerTest.assertInvalid`가 필드 에러 배열의 첫 항목만 봐서, 한 요청이 검증 두 개에 걸리면(참가자 1명 → 벌칙자 수 검증도 실패) 순서에 따라 가끔 실패했다 → 순서와 무관하게 해당 필드가 있는지 보도록 고쳤다


---

## Phase 7: 벌칙자 확정·다시 돌리기

- **목표:** 결과 공개 시 `Penalty`(`ASSIGNED`) 생성, 다시 돌리기(권한·복제·`rerunOf` 유니크), 새 회차 공개 시 `VOIDED`·규칙 이관·조건 상태 상속, 새 회차 취소
- **포함 기능 ID:** R-4, R-5, R-10

### Task 031: 벌칙·벌칙 규칙 도메인 골격   ✅ 완료

- **완료일:** 2026-11-11 · 브랜치 `feature/penalty-domain`
- **관련 기능:** R-5, R-9, 4.5 벌칙 상태 전이 표
- **선행 Task:** Task 026
- **구현 사항**
  - [x] `penalty/domain/Penalty`(gameSession, member, status `ASSIGNED/APPLIED/WAIVED/VOIDED`, waivedBy?, waivedAt?, `@Version`) — `(gameSession, member)` 유니크, `chargedAmount` 필드 없음. 팩토리 `Penalty.assign`(`ASSIGNED`)
  - [x] `penalty/domain/PenaltyRule`(gameSession 유니크, moneyType `FIXED_AMOUNT/RATIO/FULL`, amount?, ratioPct?, targetExpense 필수 FK, setBy, updatedAt, `@Version`) + `moveTo(newGameSession)`(setBy·updatedAt 유지). `create`가 종류별 값 조합을 검증한다(고정 금액 1~1억·비율 1~100·전액은 값 없음, 위반 시 400 `INVALID_INPUT`)
  - [x] `Penalty` 전이 메서드(4.5 표 전체): `apply`(ASSIGNED·WAIVED →), `waive(waivedBy, waivedAt)`(ASSIGNED·APPLIED →), `unwaive`, `unapply`, `voidOut`(종단), 허용 안 된 전이는 409 `PENALTY_NOT_CONFIGURABLE`
  - [x] Repository: `PenaltyRepository.findAllByGameSessionIdAndStatusNot(VOIDED)`, `PenaltyRuleRepository.findByGameSessionId`, `findAllByTargetExpenseId`
  - [x] 에러 코드: `PENALTY_NOT_CONFIGURABLE`(409)
  - [x] ⚠️ 결정(2026-11-11, ROADMAP 미정 항목)
    - `void`는 Java 예약어라 무효 전이 메서드 이름은 `voidOut()`
    - `PenaltyRule`은 `BaseTimeEntity`를 상속하지 않는다. 자동 감사(`@LastModifiedDate`)는 `moveTo`로 게임만 바꿔도 `updatedAt`을 갱신하므로, `updatedAt`을 도메인이 직접 넣는 일반 컬럼으로 뒀다(`createdAt`은 없음)
    - `waivedBy`·`setBy`는 `GameSession.createdBy`처럼 FK가 아닌 회원 id 값
    - `WAIVED → APPLIED`·`WAIVED → ASSIGNED`는 면제 정보를 비우고, `VOIDED`가 될 때는 이력용으로 남긴다
    - 다시 돌린 회차의 `(생성) → APPLIED/WAIVED`는 전용 팩토리 없이 `assign` 후 `apply()`·`waive(원 회차 값)`로 만든다(Task 034)
    - 조건 값 변경 메서드(`PenaltyRule.update`)는 Task 036에서 추가
- **테스트**
  - [x] 단위(`PenaltyTest` 21건, 전이 표 전체): `ASSIGNED→APPLIED/WAIVED`, `APPLIED→ASSIGNED/WAIVED`, `WAIVED→ASSIGNED/APPLIED`, 모든 비종단 → `VOIDED`, `VOIDED`에서 어떤 전이도 불가, 면제 정보 기록·비움
  - [x] 단위(`PenaltyRuleTest` 17건): `PenaltyRule.moveTo` 후 setBy·updatedAt 불변, 종류별 경계값·잘못된 값 조합
  - [x] `@DataJpaTest`(`PenaltyRepositoryTest` 4건, `PenaltyRuleRepositoryTest` 4건): `(gameSession, member)` 유니크, `PenaltyRule.gameSession` 유니크, 비무효 벌칙 조회, 대상 지출로 조회, 이관 후 DB의 `updatedAt` 불변
- **수락 기준**
  - [x] Given `VOIDED` 벌칙 / When 어떤 전이를 시도하면 / Then 409로 거부된다. — `PenaltyTest.voidedIsTerminal`
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 1045건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 새 테이블 `penalty`·`penalty_rule`은 로컬 `ddl-auto=update`로 자동 생성된다. `penalty_rule.target_expense_id` FK 때문에 규칙이 걸린 지출은 삭제가 막히는데, 규칙을 만드는 API(Task 036)가 아직 없어 지금은 영향이 없고 삭제 연동은 Task 037에서 한다

### Task 032: 결과 공개 시 벌칙자 확정   ✅ 완료

- **완료일:** 2026-11-12 · 브랜치 `feature/penalty-assign`
- **관련 기능:** R-5, R-4
- **선행 Task:** Task 027, Task 031
- **구현 사항**
  - [x] `penalty/service/PenaltyAssigner.onRevealed(gameSession, actorId, penalizedMembers)` — 공개 전이와 같은 트랜잭션(`MANDATORY`). `penaltyKind = NONE`이면 생성 없음, `MONEY`·`ACTION`이면 벌칙자별 `ASSIGNED`를 **memberId 오름차순** insert
  - [x] 금액형이면 `SettlementGuard.lockForAmountChange` + `markAmountChanged` + 최초 공개 `ChangeLog`(게임당 1건)
  - [x] 공개 중복 실행 방어: `(gameSession, member)` 유니크 + `GameSession.version`(늦은 시작은 version flush에서 떨어져 벌칙 insert까지 오지 않는다)
  - [x] `GameResult.outcome = PENALTY`인 참가자가 벌칙자다(Task 027 결정). 공개 전이는 `GameLobbyService.start`에서 일어나고, `start`가 결과 저장 직후 `onRevealed`를 부른다
  - [x] `game/dto/GameRevealSnapshot`(status, type, penaltyKind, penaltyMemberIds) — 변경 로그 after 값
  - [x] ⚠️ 결정(2026-11-12, ROADMAP 미정 항목)
    - `onRevealed`는 게임만 받지 않고 **행위자와 뽑힌 벌칙자**도 받는다. 변경 로그에 행위자가 필요하고, 벌칙자는 `start`가 이미 알고 있어 결과를 다시 읽지 않는다
    - 최초 공개 `ChangeLog`는 새 enum 값을 만들지 않고 금액형 취소와 같은 `GAME_SESSION` + `UPDATE`로 남긴다. before는 `GameChangeSnapshot`(`WAITING`), after는 `GameRevealSnapshot`(`REVEALED` + 벌칙자 id)이라 after로 구분한다. 벌칙자별이 아니라 게임당 1건
    - "재확정 필요" 이벤트의 변경 요약도 `(GAME_SESSION, gameId, UPDATE)` 1건
- **테스트**
  - [x] 단위(`PenaltyAssignerTest` 4건): 금액형 벌칙자 2명(민수·영희) → `Penalty` 2건 `ASSIGNED`, `revealedAt`·`applyOrderAt` 기록, 정산 반영 뒤 `ChangeLog` 1건 / `NONE` → 아무 상호작용 없음 / `ACTION` → `ASSIGNED`지만 정산·변경 로그 무관 / 입력 순서를 섞어도 memberId 오름차순 저장
  - [x] 단위(`GameLobbyServiceTest`): 시작 시 뽑힌 벌칙자로 `onRevealed` 호출, 재시작 거부 시 미호출
  - [x] 통합(DB, `GameLobbyIntegrationTest` 5종 추가): `CONFIRMED` 정산에서 금액형 공개 → `REOPENED`·`ChangeLog` 1건·`SETTLEMENT_REOPENED` 전송·벌칙 insert 순서, 행동형 공개 → 정산 상태·version 그대로, `NONE` → 벌칙 0건, 금액형 동시 시작(5회 반복) → 1인 1건·`ChangeLog` 1건, 공개 처리 재실행 → 유니크 위반으로 1인 1건 유지
- **수락 기준**
  - [x] Given `penaltyKind = MONEY` 사다리, 결과 "꽝" 2명(민수·영희) / When 결과가 공개되면 / Then 민수·영희 각각 `Penalty(gameSession, member)`가 `ASSIGNED`로 생성되고 금액은 계산·저장되지 않으며, `revealedAt`·`applyOrderAt`이 기록된다. 공개 처리가 중복 실행돼도 `(gameSession, member)` 유니크로 1인 1건이며, `Penalty`는 `memberId` 오름차순으로 생성된다. 금액형 게임의 최초 결과 공개는 `ChangeLog`에 기록된다. (PRD R-5 앞부분) — `PenaltyAssignerTest.moneyGameAssignsAndReflectsToSettlement`, `GameLobbyIntegrationTest.moneyRevealAssignsPenaltyAndReopens`·`duplicateRevealHandlingRejected`
  - [x] 벌칙 종류 "없음"으로 만든 게임은 결과가 공개돼도 `Penalty`가 생성되지 않는다. (PRD R-4) — `GameLobbyIntegrationTest.noneRevealCreatesNoPenalty`
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 1058건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 이제 `MONEY`·`ACTION` 게임을 시작하면 `penalty` 행이 생기므로, 게임을 시작하는 통합 테스트는 정리할 때 `game_session`보다 먼저 `penalty`를 지워야 한다. Task 034는 `onRevealed` 안에 `rerunOf` 분기를 더한다

### Task 033: 다시 돌리기 요청과 다시 돌린 회차 취소   ✅ 완료

- **완료일:** 2026-11-12 · 브랜치 `feature/game-rerun`
- **관련 기능:** R-5, R-10, E3
- **선행 Task:** Task 032
- **구현 사항**
  - [x] `game/service/GameRerunService.request(originalId, requesterId)` — 권한(`GameAccessChecker.requireOperator`: 원 회차 진행자 또는 호스트, 그 외 403 `NOT_GAME_OPERATOR`, 비멤버 403 `NOT_GROUP_MEMBER`) → 사전 검사(`REVEALED`/`CLOSED`이면서 후속 회차 없음, 아니면 409 `GAME_NOT_RERUNNABLE`) → `GameSession.rerunOf(original, requesterId)`(`WAITING`, rerunOf, createdBy = 요청자, applyOrderAt·rootSessionId 상속, type·penaltyKind·penaltyNote·penaltyCount 복제) `saveAndFlush`, 유니크 위반 → 409 `RERUN_ALREADY_EXISTS` → 참가자 복제
  - [x] 요청 DTO 자체가 없다(`POST /api/games/{gameId}/rerun`, 본문 없음, 201) — type·penaltyKind·penaltyNote·참가자를 바꿀 길이 없음
  - [x] 원 회차 행은 수정하지 않음(락 없음), 원 `Penalty`·`PenaltyRule` 그대로
  - [x] 금액형이면 `lockForAmountChange` + `markAmountChanged` + `ChangeLog`
  - [x] 취소 확장: `GameSession.cancel()`이 다시 돌린 회차면 `rerunOf`를 비우고 `canceledRerunOf` = 원 회차로 남긴다(서비스가 아니라 전이 메서드에 둬서 빠뜨릴 수 없게)
  - [x] 게임 이력 조회에 재실행·취소 이력 공개: `GameStateResponse`에 `rootSessionId`·`rerunOfId`·`canceledRerunOfId` 추가 — 상태 조회·차수 게임 목록·실시간 메시지에 같이 실린다
  - [x] `GameSessionRepository.existsByRerunOfId`, `game/dto/GameRerunSnapshot`(status, type, penaltyKind, rerunOfId)
  - [x] 에러 코드: `GAME_NOT_RERUNNABLE`, `RERUN_ALREADY_EXISTS`(409)
  - [x] ⚠️ 결정(2026-11-12, ROADMAP 미정 항목)
    - 다시 돌리기 `ChangeLog`는 새 enum 값 없이 `GAME_SESSION` + `CREATE`, entityId = **새 회차** id, before 없음, after = `GameRerunSnapshot`. 일반 게임 생성은 로그를 남기지 않으므로 `GAME_SESSION` `CREATE`가 곧 다시 돌리기 요청이다
    - 이력 공개는 별도 API 없이 기존 응답에 필드 3개를 더하는 방식. 같은 `rootSessionId`끼리 묶으면 한 게임의 회차 이력이다
    - 벌칙자 수(`penaltyCount`)도 원 회차 값을 복제한다. 참가자는 그대로 복제하고 참석 여부를 다시 검사하지 않는다(Task 027 결정과 같음)
    - 새 회차 생성 알림은 일반 생성처럼 **모임 토픽**으로만 보낸다(원 회차 게임 토픽에는 다른 게임의 상태를 보내지 않는다)
- **테스트**
  - [x] 단위(`GameRerunServiceTest` 11건): 호스트·진행자 요청 성공, 필드 복제·상속, 참가자 복제, 원 회차 불변 / 403 `NOT_GAME_OPERATOR` / `WAITING`·`PLAYING`·`CANCELED`·후속 회차 있음 → 409 `GAME_NOT_RERUNNABLE`, 아무것도 생성 안 됨 / 유니크 위반 → `RERUN_ALREADY_EXISTS` / 금액형만 정산 반영·`ChangeLog`
  - [x] 단위(`GameSessionTest` 8건 추가): `rerunOf` 팩토리, 다시 돌린 회차 취소 시 `rerunOf` 비움·`canceledRerunOf` 기록, `isRerunnable`
  - [x] `@WebMvcTest`(`GameControllerTest` 3건 추가): 201·`rerunOfId`, 403, 409 두 종류, 비로그인 401
  - [x] `@DataJpaTest`(`GameSessionRepositoryTest` 1건 추가): `existsByRerunOfId`(취소 후 false)
  - [x] 통합(DB, `GameRerunIntegrationTest` 6종): 복제·상속과 원 `Penalty` 불변, 비진행자 403, 사전 검사 409, 동시 2건(5회 반복) → 1건 성공·늦은 쪽 409 `RERUN_ALREADY_EXISTS` 또는 `GAME_NOT_RERUNNABLE`, `CONFIRMED` 정산 금액형 다시 돌리기 → `REOPENED` + `ChangeLog`, 다시 돌린 회차 취소 → 원 `Penalty` 불변·원 회차 재요청 가능·목록에 취소 이력
- **수락 기준**
  - [x] When 원 회차 진행자 또는 호스트가 "다시 돌리기"를 요청하면 / Then `rerunOf`가 원 회차를 가리키고 `applyOrderAt`을 이어받은 새 `GameSession`(`WAITING`, `createdBy` = 요청자, `type`·`penaltyKind`·`penaltyNote`·참가자 목록은 원 회차 복제)이 생기고, 원 회차 `Penalty`는 아직 바뀌지 않는다. 진행자·호스트가 아닌 그룹 멤버가 요청하면 403(`NOT_GAME_OPERATOR`), 원 회차가 `REVEALED`/`CLOSED`가 아니거나 이미 후속 회차가 있으면 409(`GAME_NOT_RERUNNABLE`)를 받고 아무것도 생성되지 않는다. 같은 회차에 다시 돌리기 요청 2건이 동시에 오면 1건만 성공하고, 늦은 쪽은 409 `RERUN_ALREADY_EXISTS` 또는 409 `GAME_NOT_RERUNNABLE` 중 하나를 받는다. Given 정산이 `CONFIRMED`인 모임의 금액형 게임 / When 다시 돌리기를 요청하면 / Then 새 회차가 생성되고 같은 트랜잭션에서 `Settlement`가 `REOPENED`가 되며 `ChangeLog`에 다시 돌리기가 기록된다. (PRD R-5) — `GameRerunIntegrationTest.rerunCopiesOriginal`·`rerunRejectsNonOperator`·`rerunRejectsNotRerunnable`·`concurrentRerun`·`moneyRerunReopensSettlement`
  - [x] Given 다시 돌린 회차(`WAITING`) / When 취소하면 / Then 원 회차 `Penalty`·`PenaltyRule`은 바뀌지 않고, 원 회차에 다시 돌리기를 새로 요청할 수 있다. (PRD R-10) — `GameRerunIntegrationTest.cancelRerunKeepsOriginal`
- **완료 조건**
  - [x] `./gradlew test` 통과(전체 1091건) · [x] `/spring-tools:validate` 통과(진단 0건) · [x] 수락 기준 충족
- **참고:** 다시 돌린 회차를 **시작**하면 지금은 새 벌칙자가 `ASSIGNED`로만 생기고 원 회차 벌칙이 그대로 남아 벌칙이 겹친다. `VOIDED`·규칙 이관·조건 상속은 Task 034에서 넣으므로 그 전에는 다시 돌린 회차를 시작하는 흐름을 배포하지 않는다. `game_session`은 자기 참조 FK(`rerun_of_id`·`canceled_rerun_of_id`)가 있어 통합 테스트 정리 때 두 컬럼을 먼저 비우고 지운다

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
  - [x] 게임 시작 동시 요청(추첨 1회) — Task 027 `GameLobbyIntegrationTest`
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
| Task 050 | 1차 장소 반경 2차 장소 추천 ⏸️ | P-7 | 043 | 무저장 원칙상 1차 장소 좌표가 없어 PRD에서 보류 — v0.2 착수 시 재설계 |

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
| Task 057 | 그룹 단골 장소·지난 장소 불러오기 | P-8 | 043 | — (사용자 입력 `Place`만 저장) |
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
| Task 064 | 중간 지점 추천 ⏸️ | P-10 | 043 | 무저장 원칙상 PRD에서 보류 — v1.0 착수 시 재설계 |
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
| R-1 | P0 | 027 |
| R-2 | P0 | 027 |
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
| 5. 카카오 로컬 데이터 저장 범위 | 014, 016, 019, 050, 057, 064 | **종결(2026-11-04)** — 문의 없이 카카오 데이터 무저장. 050·064는 보류(재설계) |
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
