# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`moyeo`는 Spring Boot 4.1 + Java 21 기반 친구 모임 관리 웹앱의 백엔드 프로젝트다. 제품 요구사항은 `docs/PRD.md`에 있다.

### 코드 구조 규칙

- `global/auth/GroupAccessChecker.requireMember(groupId, memberId)` — 그룹 리소스 Service는 진입 시 이걸로 멤버 여부를 검사한다(비멤버·없는 그룹 모두 403 `NOT_GROUP_MEMBER`)
- 실시간 알림은 Service 트랜잭션 안에서 `RealtimeEvent` 구현 record를 `ApplicationEventPublisher`로 발행만 한다(전송은 `global/event/RealtimeBroadcaster`가 커밋 후에). 새 토픽 종류를 열면 `global/websocket/TopicGroupResolver` 구현을 같이 등록한다(없으면 구독이 거부됨)
- 도메인 패키지는 `<도메인>/{domain,dto,repository,service,controller}` 구조를 따른다.
- 진행 상황과 다음 작업은 `docs/ROADMAP.md`를 본다.

## 명령어

이 프로젝트는 Gradle을 쓴다. 전역 설정에 적힌 `npm run ...` 명령은 여기서 해당하지 않는다(`./gradlew build`/`test`/`bootRun`).

별도 린트 설정(Checkstyle, Spotless 등)은 없다.

## 설정 및 환경

- `application.properties`에는 아직 datasource 설정이 없다. MySQL 드라이버와 JPA가 포함돼 있어 DB 접속 정보 없이 실행하거나 `@SpringBootTest`(`contextLoads`)를 돌리면 컨텍스트 로딩이 실패할 수 있다.
- 로컬 DB 접속 정보 같은 개인 설정은 프로젝트 루트의 `config/application-local.properties`에 둔다(gitignore 대상). `local` 프로필로 활성화한다. `src/main/resources`에 두면 jar에 비밀값이 묶이므로 두지 않는다(Spring Boot가 실행 위치의 `./config/`를 자동으로 읽으니 작업 디렉터리는 프로젝트 루트여야 한다). 카카오 키(`kakao.oauth.*`), `auth.jwt.secret`(32바이트 이상), `auth.cookie.secure=false`(http 로컬)도 여기에 둔다.
- 공개 경로는 `SecurityConfig`의 화이트리스트에만 있고, 나머지 API는 전부 인증이 필요하다. 새 공개 API를 만들면 여기에 추가한다.
- 테스트 작성 규칙은 `src/test/CLAUDE.md`에 있다(테스트 파일을 다룰 때 로드).
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
- `dev`로 merge한 뒤에는 그 `feature/` 브랜치를 `git branch -d`로 지운다. `-d`는 merge되지 않은 브랜치면 거부하므로 `-D`(강제 삭제)는 쓰지 않는다. 원격에 올린 브랜치였다면 원격 브랜치도 지운다.
- 다음 작업은 merge된 `dev`에서 다시 새 `feature/` 브랜치를 만든다.
- 파일 수정 후 바로 커밋하지 않는다. 커밋·merge는 사용자가 요청할 때만 한다.
- 커밋 메시지는 한글, 브랜치명은 `feature/기능명` 형식.

## 도구

- Spring 관련 변경 후에는 `/spring-tools:validate`로 검증한다.
