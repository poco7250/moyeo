# Development Guidelines (moyeo)

> AI Agent 전용 규칙 문서. 모든 작업 전에 읽고 따른다. 응답·주석·문서·커밋 메시지는 **한국어**로 작성한다.

## 1. 프로젝트 개요

- 목적: Spring + Java 기반 **모임(Meeting) 서비스** 웹 애플리케이션
- 렌더링 방식: **서버 사이드 렌더링(Thymeleaf)**. REST API 서버가 아니다
- 베이스 패키지: `com.poco7250.moyeo`
- 구현 완료 기능: 회원가입(`member` 도메인)
- 미구현: 로그인/인증(Spring Security 필터 체인 없음), 모임 도메인 전체

## 2. 기술 스택 및 버전 (기준일 2026-10-25, `./gradlew dependencies`로 확인)

| 구분 | 기술 | 버전 |
|---|---|---|
| 언어 | Java (toolchain) | **21** (로컬 JDK 21.0.13) |
| 빌드 | Gradle (wrapper) | **9.8.2** |
| 프레임워크 | Spring Boot | **4.1.2** |
| | Spring Framework | 7.0.10 |
| | io.spring.dependency-management | 1.1.7 |
| 웹 | spring-boot-starter-webmvc / Tomcat | Tomcat 11.0.27 |
| 뷰 | Thymeleaf | 3.1.5.RELEASE |
| 영속성 | Spring Data JPA | 4.1.2 |
| | Hibernate ORM | 7.4.13.Final |
| | Jakarta Persistence API | 3.2.0 |
| | HikariCP | 7.0.2 |
| DB | MySQL (`mysql-connector-j`) | Boot BOM 관리 버전 |
| 검증 | Hibernate Validator (Bean Validation) | 9.1.5.Final |
| JSON | Jackson | **3.1.7** (패키지 `tools.jackson.*`) |
| 보안 | spring-security-crypto (BCrypt만 사용) | Boot BOM 관리 버전 |
| 로깅 | SLF4J + Logback (Lombok `@Slf4j`) | Logback 1.5.38 |
| 편의 | Lombok, DevTools | Boot BOM 관리 버전 |
| 테스트 | JUnit Jupiter | **6.0.3** |
| | Mockito | 5.23.0 |
| | AssertJ | 3.27.7 |

### 버전 관련 규칙

- **의존성에 버전을 직접 적지 마라.** Boot BOM이 관리하는 버전을 쓴다. BOM에 없는 라이브러리만 버전을 명시한다
- Boot 4는 스타터가 모듈 단위로 분리돼 있다. 3.x 이름을 쓰지 마라
  - ✅ `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`
  - ❌ `spring-boot-starter-web`, `spring-boot-starter-test` 단독 의존
- Jackson 3은 `tools.jackson.*` 패키지다. ❌ `com.fasterxml.jackson.databind.*` import 금지 (어노테이션 `com.fasterxml.jackson.annotation`은 예외)
- 테스트 슬라이스 import 경로가 3.x와 다르다. ✅ `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- ❌ `@MockBean` 사용 금지 → ✅ `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`)
- API·import 경로가 불확실하면 **Context7으로 Boot 4 / Spring 7 문서를 조회**한 뒤 작성한다. 3.x 예제를 그대로 옮기지 마라
- 버전 업그레이드 시 이 표를 함께 갱신한다

## 3. 디렉터리 구조

```
src/main/java/com/poco7250/moyeo/
├── MoyeoApplication.java          # 어노테이션 추가 금지 (설정은 global/config로)
├── global/
│   ├── common/    BaseTimeEntity  # createdAt, updatedAt 공통 엔티티
│   ├── config/    JpaAuditingConfig, PasswordEncoderConfig
│   ├── controller/HomeController  # "/" 메인
│   └── error/     ErrorCode, BusinessException, GlobalExceptionHandler
└── {도메인}/                       # member, meeting, ...
    ├── controller/
    ├── service/
    ├── repository/
    ├── domain/    # 엔티티, 도메인 enum
    └── dto/
src/main/resources/
├── application.properties         # 공통 설정 (커밋 대상)
├── application-local.properties   # 로컬 DB 접속 정보 (gitignore, 기본 프로필)
├── templates/{도메인}/*.html, fragments/layout.html, error/*.html
└── static/css/style.css
src/test/java/...                  # main과 동일한 패키지 구조
```

- 새 도메인은 **반드시 `{도메인}/{controller,service,repository,domain,dto}` 구조**로 만든다
- 여러 도메인이 공유하는 코드만 `global/`에 둔다
- ❌ `controller/`, `service/` 같은 계층 최상위 패키지 생성 금지 (계층별이 아니라 도메인별)

## 4. 코드 표준

### 공통

- 들여쓰기: **탭**. 기존 파일 스타일을 따른다
- 변수·메서드명 camelCase, 클래스 PascalCase, 상수 UPPER_SNAKE_CASE
- 모든 클래스와 public/private 메서드에 **한 줄 한국어 Javadoc**(`/** ... */`)을 붙인다
- 메서드는 **30줄 이하**. 넘으면 private 메서드로 분리한다
- ❌ `System.out.println`, `e.printStackTrace()` 금지 → ✅ `@Slf4j` + `log.info/warn/error`
- 로그에 비밀번호·이메일 원문 등 개인정보를 남기지 마라. ID만 남긴다 (예: `memberId={}`)
- 의존성 주입은 `private final` 필드 + `@RequiredArgsConstructor`. ❌ `@Autowired` 필드 주입 금지 (테스트 코드 제외)

### 엔티티 (`domain/`)

- `BaseTimeEntity`를 상속한다
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Getter`만 붙인다. ❌ `@Setter`, `@Data`, `@AllArgsConstructor` 금지
- 생성은 **정적 팩토리 메서드**(`create(...)`)로, 상태 변경은 **의도가 드러나는 도메인 메서드**로 한다
  - ✅ `meeting.close()`, `meeting.changeInfo(title, description)`
  - ❌ `meeting.setStatus(CLOSED)`
- PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`
- 모든 컬럼에 `nullable`, `length`를 명시한다. DTO 검증 길이와 일치시킨다
- enum 컬럼은 **`@Enumerated(EnumType.STRING)`** 필수. ❌ ORDINAL 금지
- 연관관계 규칙
  - `@ManyToOne(fetch = FetchType.LAZY)` 필수. ❌ EAGER 금지
  - ❌ `@ManyToMany` 금지 → 중간 엔티티로 풀어낸다 (예: `MeetingParticipant`)
  - 양방향은 꼭 필요할 때만. 만들면 연관관계 편의 메서드를 한쪽에 둔다
  - 엔티티 간 `cascade`는 생명주기가 완전히 종속될 때만 (예: Meeting → MeetingParticipant의 `PERSIST` 정도). ❌ `CascadeType.ALL`을 습관적으로 붙이지 마라
- 엔티티에 `toString()`을 Lombok으로 만들지 마라 (연관관계 순환·지연로딩 발생)

### DTO (`dto/`)

| 용도 | 형태 | 이름 예 |
|---|---|---|
| 폼 입력(Thymeleaf `th:field` 바인딩) | **클래스** + `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` | `MeetingCreateRequest` |
| 화면 출력 | **record** + `static from(Entity)` | `MeetingResponse`, `MeetingDetailResponse` |
| 검색 조건 | 클래스 (쿼리 파라미터 바인딩) | `MeetingSearchCondition` |

- 이름 규칙: `{도메인}{동작}Request`, `{도메인}{용도}Response`
- 검증은 Bean Validation 어노테이션 + 한국어 `message`로 작성한다. 필드 간 검증(비밀번호 확인, 시작<종료 시간 등)은 DTO에 `isXxx()` 메서드를 두고 컨트롤러에서 `bindingResult.rejectValue`로 처리한다
- ❌ 엔티티를 Model/뷰에 직접 넘기지 마라. 반드시 Response DTO로 변환한다
- ❌ Response DTO에 비밀번호 등 민감 정보 포함 금지

### Repository (`repository/`)

- `JpaRepository<Entity, Long>`를 상속하는 인터페이스
- 존재 확인은 `existsByXxx`, 단건 조회는 `Optional<T> findByXxx`
- 목록 조회는 `Pageable`/`Page`(또는 `Slice`)를 받는다. ❌ 전체 `findAll()` 화면 노출 금지
- 연관 엔티티를 함께 쓰는 조회는 `@Query` + `join fetch` 또는 `@EntityGraph`로 **N+1을 막는다**
- 컬렉션 fetch join과 페이징을 같이 쓰지 마라 → `@BatchSize` 또는 ID 목록 조회 후 별도 조회

### Service (`service/`)

- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`
- 반환 타입은 **DTO**. 엔티티를 컨트롤러로 반환하지 마라 (예외: 같은 도메인 서비스 내부 private 메서드)
- 비즈니스 규칙 위반은 `throw new BusinessException(ErrorCode.XXX)`
- 엔티티 조회 실패는 `orElseThrow(() -> new BusinessException(ErrorCode.XXX_NOT_FOUND))`
- 다른 도메인 데이터가 필요하면 **그 도메인의 Repository를 직접 주입해 조회**해도 된다. 단 다른 도메인 엔티티의 상태 변경은 해당 도메인 메서드를 통해서만

### Controller (`controller/`)

- `@Controller` + `@RequestMapping("/{도메인 복수형}")` (예: `/members`, `/meetings`)
- 뷰 이름은 `private static final String XXX_VIEW = "{도메인}/{화면}"` 상수로 둔다
- POST 성공 시 **반드시 PRG**: `return "redirect:..."` + 필요 시 `RedirectAttributes.addFlashAttribute`
- 폼 처리 순서 (MemberController.join 패턴 그대로):
  1. `@Valid @ModelAttribute("xxxRequest")` + `BindingResult`
  2. 필드 간 검증 → `rejectValue`
  3. `hasErrors()`면 폼 뷰 반환
  4. 서비스 호출, 폼에 다시 보여줄 `BusinessException`만 catch해서 `bindingResult.reject(code.name(), message)`
- 컨트롤러에 비즈니스 로직·Repository 직접 호출 금지

### 템플릿 (`templates/`)

- 모든 페이지는 `fragments/layout.html`의 `head(title)`, `header`, `footer`를 `th:replace`로 쓴다
- 파일명은 camelCase (`joinForm.html`, `meetingDetail.html`)
- 폼 에러: 필드 에러는 `th:errors`, 전역 에러는 `#fields.globalErrors()`
- 링크는 `th:href="@{...}"`. ❌ 하드코딩된 절대 URL 금지
- 스타일은 `static/css/style.css`에 추가한다. ❌ 인라인 `<style>`·외부 CSS 프레임워크 CDN 추가 금지 (도입하려면 사용자 확인)
- 사용자 입력 출력은 `th:text`만. ❌ `th:utext` 금지 (XSS)

## 5. 에러 처리 표준

- 새 에러는 **`global/error/ErrorCode` enum에만 추가**한다. 도메인 주석 섹션(`// 회원`, `// 모임`)으로 묶는다
  - ✅ `MEETING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.")`
- 이름 패턴: `{대상}_{상황}` (`MEETING_FULL`, `NOT_MEETING_HOST`, `ALREADY_JOINED_MEETING`)
- ❌ 도메인별 커스텀 예외 클래스 생성 금지. `BusinessException` 하나로 통일한다
- ❌ 서비스에서 `IllegalArgumentException`/`RuntimeException` 직접 throw 금지
- 폼과 무관한 에러(조회 실패, 권한 없음)는 catch하지 말고 `GlobalExceptionHandler` → `error/common.html`로 보낸다
- 새 예외 유형 처리가 필요하면 `GlobalExceptionHandler`에 `@ExceptionHandler`를 추가하고 `errorView(ErrorCode)`를 재사용한다

## 6. 모임 도메인 규칙 (`meeting` 패키지)

### 모델 기준

| 엔티티 | 핵심 필드 | 비고 |
|---|---|---|
| `Meeting` | title, description, location, startAt, maxParticipants, status, host(`Member`) | `@ManyToOne LAZY host` |
| `MeetingParticipant` | meeting, member, role | (meeting_id, member_id) **유니크 제약** |
| `MeetingStatus` enum | `RECRUITING`, `CLOSED`, `CANCELED` (필요 시 `FINISHED`) | STRING 저장 |
| `ParticipantRole` enum | `HOST`, `MEMBER` | |

- 모임 생성 시 **개설자도 `MeetingParticipant(HOST)`로 함께 저장**한다 (같은 트랜잭션)
- 일시는 `LocalDateTime`, 타임존은 `Asia/Seoul` 기준 (JDBC URL `serverTimezone=Asia/Seoul`)
- 현재 인원은 `MeetingParticipant` count로 계산한다. 비정규화 컬럼(`currentCount`)을 추가하면 참가/취소 메서드에서 반드시 함께 증감한다

### 비즈니스 규칙 (서비스·엔티티에서 강제)

- 참가 시 검증 순서: 모임 존재 → `RECRUITING` 상태 → 이미 참가 여부 → 정원 초과 여부
- **정원 체크와 참가 저장은 동시성 제어 필수**
  - ✅ 참가 처리 시 `Meeting`을 `@Lock(LockModeType.PESSIMISTIC_WRITE)`로 조회한 뒤 count → 저장
  - ✅ 중복 참가는 DB 유니크 제약으로 최종 방어 (`DataIntegrityViolationException`은 이미 핸들러가 처리)
  - ❌ 락 없이 `count < max` 확인 후 save 금지
- 수정·삭제·마감은 **호스트만** 가능. 권한 검사는 서비스에서 `meeting.isHost(memberId)` 같은 도메인 메서드로 한다
- 호스트는 참가 취소 불가 (모임 취소로 처리)
- 지난 모임(`startAt` 과거)에는 참가 불가
- 모임 삭제는 물리 삭제 대신 `status = CANCELED`로 처리한다 (참가 이력 보존)

### URL 기준

| 동작 | 메서드 | URL |
|---|---|---|
| 목록 | GET | `/meetings` (`page`, `size`, 검색 조건) |
| 생성 폼/처리 | GET/POST | `/meetings/new` |
| 상세 | GET | `/meetings/{meetingId}` |
| 수정 폼/처리 | GET/POST | `/meetings/{meetingId}/edit` |
| 참가 / 취소 | POST | `/meetings/{meetingId}/join`, `/meetings/{meetingId}/leave` |
| 마감 / 취소 | POST | `/meetings/{meetingId}/close`, `/meetings/{meetingId}/cancel` |

- 상태 변경은 **모두 POST**. ❌ GET 링크로 참가·삭제 처리 금지

## 7. 인증(로그인) 도입 규칙

- 현재 Spring Security 필터 체인이 없다. 로그인이 필요한 기능(모임 생성·참가)을 만들기 전에 **인증 방식을 먼저 사용자에게 확인**한다 (세션 직접 구현 vs `spring-boot-starter-security` 도입)
- 도입 전까지 임시로 회원 ID를 파라미터·하드코딩으로 받는 코드를 만들지 마라
- 로그인 회원 정보는 컨트롤러 파라미터로 받아 서비스에 `memberId`(Long)로 넘긴다. ❌ 서비스가 세션/`SecurityContextHolder`에 직접 접근 금지
- 비밀번호 비교는 `PasswordEncoder.matches`만 사용

## 8. 테스트 표준

- 새 Service·Controller에는 테스트를 함께 작성한다. 위치는 main과 같은 패키지
- 테스트 메서드명은 **한국어 문장 + 언더스코어** (`정원이_가득_차면_참가할_수_없다`)
- Service 단위 테스트: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` + **BDDMockito `given`** + AssertJ
- Controller 테스트: `@WebMvcTest(XxxController.class)` + `@MockitoBean` + `MockMvc`
- Repository/쿼리 테스트: `@DataJpaTest` (`spring-boot-starter-data-jpa-test`)
- 동시성 규칙(정원)은 `@SpringBootTest` + 멀티스레드 테스트로 검증한다. DB가 필요하므로 실행 조건을 테스트 주석에 적는다
- 예외 검증: `assertThatThrownBy(...).isInstanceOf(BusinessException.class)` + `ErrorCode` 비교
- `MoyeoApplicationTests.contextLoads`는 로컬 MySQL이 떠 있어야 통과한다. 실패 시 DB 상태부터 확인한다

## 9. 설정 파일 규칙

| 파일 | 넣을 것 | 금지 |
|---|---|---|
| `application.properties` | 프로필 무관 공통 설정 | ❌ DB 비밀번호·접속 정보 |
| `application-local.properties` | 로컬 DB 접속, `ddl-auto=update`, `show-sql` | 커밋 금지 (gitignore) |

- `spring.jpa.open-in-view=false`를 유지한다. 뷰에서 쓸 데이터는 서비스 트랜잭션 안에서 DTO로 다 만들어 넘긴다
- ❌ `ddl-auto=create`/`create-drop`으로 바꾸지 마라 (로컬 데이터 삭제)
- 운영 프로필을 추가하면 `ddl-auto=validate` 또는 `none`으로 둔다

## 10. 동시 수정 파일 규칙

| 작업 | 함께 수정할 파일 |
|---|---|
| 새 에러 추가 | `ErrorCode` + 사용하는 Service + 테스트 |
| 엔티티 필드 추가/변경 | 엔티티 + Request/Response DTO 검증·필드 + 템플릿 + 테스트 |
| 새 화면 추가 | Controller + `templates/{도메인}/*.html` + 필요 시 `fragments/layout.html` 네비게이션 |
| 새 전역 설정 빈 | `global/config/*Config.java` (메인 클래스에 추가 금지) |
| 의존성 추가/버전 변경 | `build.gradle` + 이 문서 2장 버전 표 + `CLAUDE.md`(주요 의존성 언급 시) |
| 패키지 구조·명령어 변경 | `CLAUDE.md` + 이 문서 3장 |

## 11. 작업 흐름

1. 관련 기존 코드(특히 `member` 도메인)를 읽고 패턴을 확인한다
2. Boot 4 API가 불확실하면 Context7로 확인한다
3. 엔티티 → Repository → Service(+테스트) → DTO → Controller(+테스트) → 템플릿 순으로 구현한다
4. `./gradlew test`로 검증한다 (npm 명령 아님)
5. Spring 관련 변경 후 `/spring-tools:validate`로 검증한다
6. 커밋은 사용자가 요청할 때만, **한글 메시지**로. 브랜치는 `feature/기능명`

## 12. AI 판단 기준

- 기존 코드 패턴과 이 문서가 충돌하면 → **이 문서를 따르고**, 차이를 사용자에게 알린다
- 새 라이브러리가 필요해 보이면 → 추가하지 말고 사용자에게 먼저 제안한다
- 비즈니스 규칙이 6장에 없으면 → 가장 보수적인 쪽(거부·제한)으로 구현하고 사용자에게 확인을 요청한다
- 화면 vs API 판단이 애매하면 → Thymeleaf 화면(`@Controller`)으로 만든다. `@RestController`는 사용자가 요청할 때만
- 동시성 영향이 있는 쓰기(정원, 중복 참가, 좋아요 수 등) → 락 또는 유니크 제약 중 최소 하나를 반드시 적용한다

## 13. 금지 사항 요약

- ❌ 엔티티 `@Setter`/`@Data`, `@ManyToMany`, EAGER, `EnumType.ORDINAL`
- ❌ 엔티티를 뷰·Model에 직접 노출
- ❌ 컨트롤러에 비즈니스 로직, 컨트롤러에서 Repository 직접 호출
- ❌ `System.out.println`, 개인정보 로깅
- ❌ 커스텀 예외 클래스 남발, `RuntimeException` 직접 throw
- ❌ Boot 3.x 스타터 이름·`@MockBean`·`com.fasterxml.jackson.databind` 사용
- ❌ 의존성 버전 직접 명시 (BOM 관리 대상)
- ❌ 락 없는 정원 체크, GET으로 상태 변경
- ❌ `application.properties`에 DB 비밀번호, `ddl-auto=create`
- ❌ `th:utext`로 사용자 입력 출력
- ❌ 사용자 요청 없이 커밋·푸시
