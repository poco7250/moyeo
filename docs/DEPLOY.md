# moyeo 배포 가이드

> Task 067(2026-11-22). PRD 오픈 이슈 1 종결 — React(Vite) 정적 SPA + 같은 도메인 + VM 1대 Docker Compose.

## 1. 구성

```
              https://moyeo.example.com
                        │
                ┌───────▼────────┐
                │  proxy (Caddy) │  80/443, Let's Encrypt 자동 발급·갱신
                └───┬────────┬───┘
   /api/* /ws /share/*      │ 나머지 경로
                    │        └──► /srv/frontend (SPA dist, 없는 경로는 index.html)
            ┌───────▼───────┐
            │  app (Spring) │  8080 (외부 비공개), Flyway로 스키마 적용
            └───────┬───────┘
            ┌───────▼───────┐
            │   db (MySQL)  │  8.4, utf8mb4, +09:00
            └───────────────┘
   볼륨: db-data(MySQL), receipts(영수증 사진), caddy-data(인증서)
```

- SPA와 API가 같은 출처라 CORS가 없고, 인증 쿠키는 `Domain` 없이 `SameSite=Lax; Secure; HttpOnly`다.
- `/actuator`는 프록시에서 404. 헬스 체크(`/actuator/health/readiness`)는 compose 내부에서만 부른다.
- 앱은 **단일 인스턴스 전제**다(인메모리 STOMP 브로커, 장소 검색 빈도 제한, 로컬 디스크 영수증). `app`을 여러 개 띄우지 않는다.
- 파일: `Dockerfile`(루트), `deploy/docker-compose.yml`, `deploy/Caddyfile`, `deploy/.env.example`.

## 2. 환경 변수 (`deploy/.env`)

`cp deploy/.env.example deploy/.env` 후 채운다. `.env`는 gitignore 대상이고 이미지에도 안 들어간다(`.dockerignore`).

| 변수 | 필수 | 설명 · 생성법 |
|---|---|---|
| `APP_DOMAIN` | ✅ | Caddy 인증서 대상 도메인. DNS A 레코드가 VM을 가리켜야 한다 |
| `APP_BASE_URL` | ✅ | `https://{도메인}`. 초대·모임·공유(OG) 링크와 카카오 Redirect URI(`{APP_BASE_URL}/api/auth/kakao/callback`)가 이걸로 만들어진다 |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | ✅ | `openssl rand -base64 24` |
| `MYSQL_DATABASE`, `MYSQL_USER` | | 기본 `moyeo` |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | ✅ | 카카오 앱 REST API 키·Client Secret |
| `KAKAO_REST_API_KEY` | | 장소 검색 키를 따로 쓸 때만. **빈 값으로 두지 말고 줄 자체를 빼야** `KAKAO_CLIENT_ID`로 대체된다 |
| `JWT_SECRET` | ✅ | 32바이트 이상. `openssl rand -base64 48` |
| `PAYMENT_ENCRYPTION_KEY` | ✅ | Base64 32바이트. `openssl rand -base64 32`. **잃으면 저장된 계좌번호를 복구할 수 없으니 VM 밖에도 따로 보관** |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | | `npx web-push generate-vapid-keys`. 비우면 웹 푸시만 꺼진다. 바꾸면 기존 브라우저 구독은 다시 받아야 한다 |
| `VAPID_SUBJECT` | | `mailto:` 연락처 |
| `WS_ALLOWED_ORIGINS` | | 같은 도메인 배포라 비운다 |
| `AUTH_COOKIE_SECURE` | | 기본 `true`. 운영에서 바꾸지 않는다 |

compose가 직접 넣는 값: `SPRING_PROFILES_ACTIVE=prod`(기본 프로필이 `local`이라 필수), `SPRING_DATASOURCE_*`, `KAKAO_REDIRECT_URI`. prod 전용 설정 파일은 없다(전부 `application.properties`의 `${ENV:}` 자리표시자).

## 3. 첫 배포

1. VM 준비: Docker Engine + Compose 플러그인, 방화벽 80·443(TCP)·443(UDP) 열기, 도메인 DNS 연결
2. 카카오 개발자 콘솔
   - 플랫폼 > Web 사이트 도메인: `https://{도메인}`
   - 카카오 로그인 > Redirect URI: `https://{도메인}/api/auth/kakao/callback`
   - 동의 항목은 로컬과 같게
3. 저장소를 받고 `./gradlew build`로 전체 테스트를 통과시킨다(이미지 빌드는 테스트를 건너뛴다)
4. `deploy/.env` 작성(2장)
5. 프론트 빌드 결과물(`npm run build`의 `dist/` 내용)을 `deploy/frontend-dist/`에 둔다
6. `cd deploy && docker compose up -d --build`
7. 확인
   - `docker compose ps` — db·app이 `healthy`
   - `docker compose logs app | grep -i flyway` — `Successfully applied ... now at version v1`
   - `curl -i https://{도메인}/api/members/me` → 401 JSON
   - 브라우저로 `https://{도메인}/` → SPA

업데이트: `git pull` → `./gradlew build` → `cd deploy && docker compose up -d --build app`. 새 마이그레이션은 앱이 뜰 때 자동 적용된다. 프론트만 바꿀 때는 `frontend-dist/`만 교체(재시작 불필요).

## 4. 스키마 마이그레이션 규칙

- 스키마는 Flyway(`src/main/resources/db/migration`)가 만들고, Hibernate는 `ddl-auto=validate`로 검사만 한다.
- 엔티티의 컬럼·제약·인덱스를 바꾸면 **`V{n}__설명.sql`을 새로 추가**한다. 이미 적용된(커밋된) 파일은 고치지 않는다(체크섬이 달라져 기동 실패).
- `SchemaMigrationTest`가 빈 MySQL에 마이그레이션을 돌리고 validate가 통과하는지 본다. 마이그레이션을 빠뜨리면 이 테스트가 깨진다. 다른 테스트는 `create-drop`이라 영향 없다.
- 테스트 전용 엔티티(`AuditingTestEntity`) 테이블은 `src/test/resources/db/test-migration`에 있다.

### 로컬 DB 전환 (한 번만)

로컬 DB는 그동안 `ddl-auto=update`로 만들어져 Flyway 이력 테이블이 없어서, 그대로 띄우면 Flyway가 "비어 있지 않은 스키마"라며 기동을 멈춘다. 둘 중 하나를 고른다.

- **새로 만들기(권장):** `DROP DATABASE moyeo; CREATE DATABASE moyeo CHARACTER SET utf8mb4;` 후 실행. 로컬 데이터는 사라진다.
- **기존 데이터 유지:** `config/application-local.properties`에 아래를 넣고 한 번 실행한 뒤 지운다. 기존 스키마를 V1로 간주하므로, 로컬 스키마가 V1과 다르면(예: 예전 컬럼이 남아 있음) validate에서 걸릴 수 있다.
  ```properties
  spring.flyway.baseline-on-migrate=true
  spring.flyway.baseline-version=1
  ```
- 그리고 로컬 파일의 `spring.jpa.hibernate.ddl-auto=update`는 `validate`로 바꾸거나 지운다(기본값이 `validate`). `update`로 두면 마이그레이션 없이 로컬에서만 스키마가 바뀌어 운영과 어긋난다.

## 5. 백업

- DB: `docker compose exec db sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction moyeo' > backup-$(date +%F).sql` (cron으로 매일, VM 밖에 보관)
- 영수증: `receipts` 볼륨(`docker run --rm -v moyeo_receipts:/data -v $PWD:/backup alpine tar czf /backup/receipts-$(date +%F).tgz -C /data .`)
- `.env`(특히 `PAYMENT_ENCRYPTION_KEY`, `JWT_SECRET`)는 비밀번호 관리자 등 VM 밖에 보관

## 6. 배포 후 수동 확인 (Task 043 남은 항목)

- [ ] 카카오톡 대화방에 초대 링크(`https://{도메인}/invites/...`)를 보내고 **카카오톡 인앱 브라우저**로 열기 → 비로그인 미리보기 → 카카오 로그인 → 같은 초대 링크로 복귀 → 가입
- [ ] 카카오톡 링크 미리보기(OG)에 그룹·모임 이름이 나오는지(`/share/...`)
- [ ] 모임 화면에서 실시간 갱신(WebSocket `wss://{도메인}/ws`) 동작
- [ ] 영수증 사진 10MB 근처 업로드 성공, 앱 재시작 후에도 조회됨(볼륨)
- [ ] 웹 푸시(VAPID를 넣은 경우) 구독·수신
