# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`moyeo`는 Spring Boot 4.1 + Java 21 기반 친구 모임 관리 웹앱의 백엔드 프로젝트다. 제품 요구사항은 `docs/PRD.md`에 있다.

### 코드 구조 규칙

- `global/auth/GroupAccessChecker.requireMember(groupId, memberId)` — 그룹 리소스 Service는 진입 시 이걸로 멤버 여부를 검사한다(비멤버·없는 그룹 모두 403 `NOT_GROUP_MEMBER`)
- 실시간 알림은 Service 트랜잭션 안에서 `RealtimeEvent` 구현 record를 `ApplicationEventPublisher`로 발행만 한다(전송은 `global/event/RealtimeBroadcaster`가 커밋 후에). 새 토픽 종류를 열면 `global/websocket/TopicGroupResolver` 구현을 같이 등록한다(없으면 구독이 거부됨)
- 도메인 패키지는 `<도메인>/{domain,dto,repository,service,controller}` 구조를 따른다.
- 진행 상황과 다음 작업은 `docs/ROADMAP.md`를 본다.
- 화면은 `src/main/resources/static`의 바닐라 JS SPA다(빌드 도구 없음, 규칙은 ROADMAP Phase 14 공통 규칙). 새 화면 경로는 `static/js/app.js`의 `route(...)`와 `global/web/SpaRoutes.SCREEN_PATHS`(index.html forward + 공개 경로)에 같이 추가한다.

## 명령어

이 프로젝트는 Gradle을 쓴다. 전역 설정에 적힌 `npm run ...` 명령은 여기서 해당하지 않는다(`./gradlew build`/`test`/`bootRun`).

별도 린트 설정(Checkstyle, Spotless 등)은 없다.

## 설정 및 환경

- `application.properties`에는 datasource 설정이 없다(로컬은 `config/application-local.properties`, 운영은 compose의 `SPRING_DATASOURCE_*`). 운영 값은 전부 `${ENV:}` 자리표시자로 받고 prod 전용 설정 파일은 만들지 않는다(`DeploymentReadinessTest`가 막음). 배포 구조·절차는 `docs/DEPLOY.md`.
- 스키마는 Flyway(`src/main/resources/db/migration`)가 만들고 Hibernate는 `ddl-auto=validate`만 한다. 엔티티 컬럼·제약·인덱스를 바꾸면 `V{n}__설명.sql`을 새로 추가하고, 이미 커밋된 마이그레이션은 고치지 않는다. 테스트는 Flyway를 끄고 `create-drop`을 쓰며, `SchemaMigrationTest`만 마이그레이션 ↔ 엔티티 일치를 검사한다.
- 로컬 DB 접속 정보 같은 개인 설정은 프로젝트 루트의 `config/application-local.properties`에 둔다(gitignore 대상). `local` 프로필로 활성화한다. `src/main/resources`에 두면 jar에 비밀값이 묶이므로 두지 않는다(Spring Boot가 실행 위치의 `./config/`를 자동으로 읽으니 작업 디렉터리는 프로젝트 루트여야 한다). 카카오 키(`kakao.oauth.*`), `auth.jwt.secret`(32바이트 이상), `auth.cookie.secure=false`(http 로컬)도 여기에 둔다.
- 공개 경로는 `SecurityConfig`의 화이트리스트에만 있고, 나머지 API는 전부 인증이 필요하다. 새 공개 API를 만들면 여기에 추가한다.
- 테스트 작성 규칙은 `src/test/CLAUDE.md`에 있다(테스트 파일을 다룰 때 로드).

## Spring Boot 4 관련 주의점

Spring Boot 4는 스타터와 자동 구성이 모듈 단위로 쪼개져 있어 3.x와 의존성 이름이 다르다.

- 웹: `spring-boot-starter-webmvc` (3.x의 `starter-web` 아님)
- 테스트 슬라이스도 기능별 테스트 스타터로 분리돼 있다: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-thymeleaf-test`
- 3.x 기준 예제의 import 경로나 어노테이션 패키지가 다를 수 있으므로, API가 불확실하면 Context7로 Boot 4 문서를 확인한다.

## Git

- 작업(shrimp task / ROADMAP Task) 하나마다 최신 `dev`에서 `feature/기능명` 브랜치를 새로 만들어 작업하고, 끝나면 `dev`로 `--no-ff` merge한다.
- `dev`로 merge한 뒤에는 그 `feature/` 브랜치를 `git branch -d`로 지운다. `-d`는 merge되지 않은 브랜치면 거부하므로 `-D`(강제 삭제)는 쓰지 않는다. 원격에 올린 브랜치였다면 원격 브랜치도 지운다.
- 다음 작업은 merge된 `dev`에서 다시 새 `feature/` 브랜치를 만든다.
- 파일 수정 후 바로 커밋하지 않는다. 커밋·merge는 사용자가 요청할 때만 한다.
- 커밋 메시지는 한글, 브랜치명은 `feature/기능명` 형식.

## 도구

- Task 하나를 마무리할 때(커밋 요청 전) 한 번 `/spring-tools:validate`로 검증한다. 수정할 때마다 돌리지 않는다.
