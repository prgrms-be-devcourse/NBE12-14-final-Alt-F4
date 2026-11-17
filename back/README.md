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