# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

`moyeo`는 Spring Boot 4.1 + Java 21 기반 백엔드 프로젝트다. 현재는 Spring Initializr로 생성한 직후의 뼈대 상태이며, 도메인 코드는 아직 없다.

- 빌드 도구: Gradle 9 (wrapper 사용)
- 주요 의존성: Spring Web MVC, Spring Data JPA, Bean Validation, Lombok, DevTools
- DB: MySQL (`mysql-connector-j`)
- 베이스 패키지: `com.poco7250.moyeo`

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
- 로컬 DB 접속 정보 같은 개인 설정은 `application-local.properties`에 둔다(gitignore 대상). `local` 프로필로 활성화한다.
- `.claude/settings.local.json`도 gitignore 대상이다.

## Spring Boot 4 관련 주의점

Spring Boot 4는 스타터와 자동 구성이 모듈 단위로 쪼개져 있어 3.x와 의존성 이름이 다르다.

- 웹: `spring-boot-starter-webmvc` (3.x의 `starter-web` 아님)
- 테스트 슬라이스도 기능별 테스트 스타터로 분리돼 있다: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-validation-test`
- 3.x 기준 예제의 import 경로나 어노테이션 패키지가 다를 수 있으므로, API가 불확실하면 Context7로 Boot 4 문서를 확인한다.

## 아키텍처 방침

사용자 전역 지침에 따라 다음 구조를 따른다.

- 레이어드 아키텍처: Controller → Service → Repository
- 계층 간 데이터 전달은 DTO 사용 (엔티티를 API 응답으로 직접 노출하지 않음)
- 생성자 주입 (Lombok `@RequiredArgsConstructor`)
- 트랜잭션 경계는 Service 계층에서 `@Transactional`로 관리
- API 응답 형식과 예외 처리는 일관된 공통 구조로 통일

## 도구

- `.mcp.json`에 프로젝트 MCP 서버가 등록돼 있다: `idea`(IntelliJ 연동), `context7`(라이브러리 문서), `shrimp-task-manager`(작업 관리, 데이터는 `shrimp_data/`).
- `.claude/settings.json`에서 `java-backend-skills`, `spring-tools` 플러그인을 활성화했다. Spring 관련 변경 후에는 `/spring-tools:validate`로 검증한다.
