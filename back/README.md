# Live-Calendar 백엔드

해당 README 문서에서는 백엔드에 해당하는 내용만 기술합니다.

프로젝트 전체에 관련된 내용은 [프로젝트 README](../README.md) 에서 확인합니다.

## 기술 스택

java 25, kotlin 2.3.21, Spring Boot 4.1.2

## 로컬 백엔드 URL  `http://localhost:8080`

| 로컬 URL | 용도 |
|---|---|
| [`/actuator`](http://localhost:8080/actuator) | HTTP로 공개된 Actuator 엔드포인트 목록을 확인합니다. |
| [`/actuator/health`](http://localhost:8080/actuator/health) | 애플리케이션 상태를 확인합니다. 정상 기동과 DB 연결이 확인되면 `UP`을 반환합니다. |
| [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) | Swagger UI 진입점입니다. |
| [`/v3/api-docs`](http://localhost:8080/v3/api-docs) | OpenAPI 명세를 JSON으로 제공합니다. |
| [`/v3/api-docs.yaml`](http://localhost:8080/v3/api-docs.yaml) | 같은 OpenAPI 명세를 YAML로 제공합니다. |
| [`localhost:3000`](http://localhost:3000/) | Grafana UI 진입점입니다. |

## 스프링부트 실행 전 준비사항

### 도커컨테이너 생성
터미널 프로젝트 루트폴더에서 명령어 실행
   ```bash
   docker compose up -d
   ```


루트폴더의 `docker-compose.yml` 파일의 설정으로  
MySQL DB, Prometheus, Grafana 와 그 볼륨들을 도커컨테이너 내에 생성합니다.

### MySQL 연결 확인

`docker compose`로 띄운 MySQL은 아래 값으로 만들어집니다. 백엔드의 `local` 프로필(`application-local.yaml`)이 같은 값을 기본값으로 쓰므로 **따로 설정하지 않아도 연결됩니다.**

| 항목 | 값 |
|---|---|
| 주소 | `localhost:3306` |
| 데이터베이스 | `live-calendar-db` |
| 계정 / 비밀번호 | `root` / `root` |

컨테이너가 떠 있는지 확인합니다. `live-calendar-db`가 목록에 보이면 준비된 것입니다.

```bash
docker exec -it local-live-calendar mysql -uroot -proot -e "SHOW DATABASES;"
```

다른 계정을 쓰려면 환경변수 `DB_USERNAME`, `DB_PASSWORD`로 바꿀 수 있습니다. 내 컴퓨터에 MySQL이 이미 3306 포트로 실행 중이면 컨테이너가 뜨지 않으니, 기존 MySQL을 먼저 끕니다.

### JWT 비밀키 설정

로그인 토큰 서명에 쓰는 `JWT_SECRET` 환경변수가 **반드시** 필요합니다. 기본값이 없어서 넣지 않으면 앱이 시작되지 않습니다.

1. 아래 명령으로 비밀키를 만듭니다. (HS256 기준 32바이트 이상이어야 합니다)
   ```bash
   openssl rand -base64 48
   ```
2. IntelliJ: `Run` → `Edit Configurations...` → `LiveCalendarApplication` → `Environment variables`에 `JWT_SECRET=만든값`을 추가합니다.

만든 값은 저장소에 커밋하지 않습니다. 각자 컴퓨터에서 한 번만 만들어 두면 됩니다.

## DB 마이그레이션 (Flyway)

테이블은 Hibernate가 아니라 `src/main/resources/db/migration`의 SQL 파일이 만듭니다. 앱이 뜰 때 Flyway가 아직 실행하지 않은 파일을 번호 순서대로 실행하고, Hibernate(`ddl-auto: validate`)는 엔티티와 테이블이 같은지만 확인합니다. 다르면 앱이 시작되지 않습니다.

### 처음 한 번: 로컬 DB 비우기

Flyway 도입 전에 만든 로컬 DB에는 Flyway 기록 테이블(`flyway_schema_history`)이 없어서 앱이 시작되지 않습니다. 한 번만 DB를 지우고 다시 만듭니다. (로컬 데이터는 사라집니다. Grafana·Prometheus는 그대로입니다)

```bash
docker exec local-live-calendar mysql -uroot -proot -e "DROP DATABASE \`live-calendar-db\`; CREATE DATABASE \`live-calendar-db\`;"
```

### 규칙

1. **엔티티(테이블·컬럼·제약·인덱스)를 바꾸면 SQL 파일을 같이 올립니다.**
   - 위치: `src/main/resources/db/migration/`
   - 이름: `V{번호}__{영문_소문자_설명}.sql` (밑줄 두 개). 예: `V3__add_room_soft_delete.sql`
2. **이미 develop에 머지된 마이그레이션 파일은 고치지 않습니다.** 고칠 게 있으면 새 번호로 추가합니다. 적용된 파일이 바뀌면 체크섬이 달라져 모두의 앱이 시작되지 않습니다.
3. **번호는 머지 직전에 develop의 마지막 번호를 보고 정합니다.** 다른 PR과 번호가 겹치면 늦게 머지하는 쪽이 번호를 올립니다. 겹친 채로 올리면 테스트가 실패합니다.
4. SQL이 맞는지는 `./gradlew test`로 확인합니다. 테스트도 Flyway로 테이블을 만들기 때문에, SQL과 엔티티가 다르면 테스트가 시작할 때 실패합니다.
   - 단, **UNIQUE 제약과 인덱스는 이 검사에서 빠집니다.** 빠뜨려도 테스트가 통과하니, 엔티티의 `@Table(uniqueConstraints, indexes)`와 SQL의 이름을 직접 맞춰 봅니다. 실제 예: `V2__add_share_public_and_unique.sql`

### 예시: 컬럼 추가

엔티티에 `deletedAt`을 추가했다면 같은 PR에 아래 파일을 넣습니다.

```sql
-- V3__add_room_soft_delete.sql
ALTER TABLE scheduling_rooms ADD COLUMN deleted_at DATETIME(6) NULL;
```

타입이 헷갈리면 `V1__init.sql`에서 같은 종류의 컬럼을 찾아봅니다(`Instant` → `datetime(6)`, `String`(길이 100) → `varchar(100)`).

## 실행과 확인

### 애플리케이션 실행

IntelliJ에서 `LiveCalendarApplication`을 실행합니다. `local` 프로필이 기본으로 켜지므로(`spring.profiles.default: local`) 프로필을 따로 지정하지 않습니다.

실행 후 [`/actuator/health`](http://localhost:8080/actuator/health)가 `UP`이면 DB 연결까지 정상입니다.

### API 호출해 보기 (IntelliJ HTTP Client)

`back/http/` 아래에 컨트롤러별 `.http` 파일이 있습니다.

1. 파일 오른쪽 위의 환경을 `local`로 선택합니다.
2. `auth/auth.http`에서 회원가입 → 로그인을 먼저 실행합니다. 받은 Access Token이 `{{accessToken}}`에 저장되고, Refresh Token 쿠키도 자동으로 저장됩니다.
3. 다른 파일(`user/user.http`, `event/event.http`)의 요청은 이 토큰으로 실행됩니다.

### API 호출해 보기 (Swagger UI)

1. [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html)을 엽니다.
2. `POST /api/v1/auth/login`을 실행하고 응답의 `accessToken` 값을 복사합니다.
3. 오른쪽 위 **Authorize**에 값을 붙여 넣습니다. `Bearer `는 Swagger가 붙이므로 토큰 값만 넣습니다.
4. 자물쇠가 표시된 API를 실행합니다. 인증 API(`/api/v1/auth/*`)는 토큰 없이 호출됩니다.

Access Token은 30분 뒤 만료됩니다. 그때는 `POST /api/v1/auth/refresh`로 새 토큰을 받아 다시 Authorize에 넣습니다(Refresh Token 쿠키는 브라우저가 자동으로 보냅니다).

### 테스트 실행

`back` 폴더에서 실행합니다 (`gradlew`가 `back` 폴더에 있습니다).

```bash
./gradlew test
```

테스트는 Testcontainers로 MySQL 컨테이너를 따로 띄웁니다. **Docker가 실행 중이어야 하고**, 위의 `docker compose` MySQL이나 `JWT_SECRET`은 필요 없습니다(테스트 전용 값이 `application-test.yaml`에 있습니다).
