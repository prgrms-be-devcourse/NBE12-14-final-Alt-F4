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

## 실행과 확인

### 애플리케이션 실행

IntelliJ에서 `LiveCalendarApplication`을 실행합니다. `local` 프로필이 기본으로 켜지므로(`spring.profiles.default: local`) 프로필을 따로 지정하지 않습니다.

실행 후 [`/actuator/health`](http://localhost:8080/actuator/health)가 `UP`이면 DB 연결까지 정상입니다.

### API 호출해 보기 (IntelliJ HTTP Client)

`back/http/` 아래에 컨트롤러별 `.http` 파일이 있습니다.

1. 파일 오른쪽 위의 환경을 `local`로 선택합니다.
2. `auth/auth.http`에서 회원가입 → 로그인을 먼저 실행합니다. 받은 Access Token이 `{{accessToken}}`에 저장되고, Refresh Token 쿠키도 자동으로 저장됩니다.
3. 다른 파일(`user/user.http`, `event/event.http`)의 요청은 이 토큰으로 실행됩니다.

### 테스트 실행

`back` 폴더에서 실행합니다 (`gradlew`가 `back` 폴더에 있습니다).

```bash
./gradlew test
```

테스트는 Testcontainers로 MySQL 컨테이너를 따로 띄웁니다. **Docker가 실행 중이어야 하고**, 위의 `docker compose` MySQL이나 `JWT_SECRET`은 필요 없습니다(테스트 전용 값이 `application-test.yaml`에 있습니다).
