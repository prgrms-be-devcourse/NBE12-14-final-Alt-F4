plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"          // @Entity 기본 생성자 자동 생성
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.team4"
version = "0.0.1-SNAPSHOT"

// Swagger jackson 의존성 취약점 대응 (CVE-2026-110879)
extra["jackson-bom.version"] = "3.1.8"
extra["jackson-2-bom.version"] = "2.21.8"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.testcontainers:testcontainers-mysql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.mnode.ical4j:ical4j:4.4.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")

    // DB 스키마를 db/migration의 SQL 파일로 관리한다 (MySQL 8은 flyway-mysql이 따로 필요)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")

    // ID 난독화 (조율방 주소, 회원 태그 생성)
    implementation("org.sqids:sqids:0.1.0")
}

allOpen {
    // Kotlin 클래스는 기본 final → Hibernate 지연 로딩 프록시를 만들 수 없음
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 테스트는 항상 test 프로필(application-test.yaml)로 실행한다
    systemProperty("spring.profiles.active", "test")
}
