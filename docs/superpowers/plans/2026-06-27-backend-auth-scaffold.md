# Backend Auth Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Spring Boot Java backend that supports Kakao social login, Runvas JWT issuance, common API errors, persisted users, and authenticated `GET /api/me`.

**Architecture:** Implement one Spring Boot modular monolith under `backend/`, starting only with `global`, `auth`, and `user`. Keep Kakao API access behind a small client interface so controller/service tests can run without real Kakao credentials. Keep JPA entities internal and map them to documented response DTOs.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Gradle, Spring Web, Spring Security, Spring Validation, Spring Data JPA, PostgreSQL, Flyway, JJWT, JUnit 5, MockMvc, Testcontainers.

---

## File Structure

Create the backend application from scratch in `backend/`.

- `backend/settings.gradle`: Gradle project name.
- `backend/build.gradle`: Spring Boot Java dependencies and test setup.
- `backend/src/main/java/com/runvas/RunvasApplication.java`: Spring Boot entry point.
- `backend/src/main/resources/application.yml`: default app config using environment variables.
- `backend/src/main/resources/db/migration/V1__create_users.sql`: initial `users` table.
- `backend/src/main/java/com/runvas/global/error/*`: documented error response shape and exception mapping.
- `backend/src/main/java/com/runvas/global/security/*`: stateless JWT filter, authentication principal, security config.
- `backend/src/main/java/com/runvas/auth/*`: Kakao login controller, DTOs, service, Kakao client, JWT provider.
- `backend/src/main/java/com/runvas/user/*`: internal user entity, provider enum, repository, user response DTO.
- `backend/src/test/java/com/runvas/auth/*`: MockMvc auth contract tests.
- `backend/src/test/java/com/runvas/user/*`: Testcontainers repository tests.
- `backend/src/test/resources/application-test.yml`: isolated test config.
- `backend/README.md`: local run/test instructions.

Do not implement course, post, comment, like, or bookmark packages in this plan.

---

### Task 1: Spring Boot Project Skeleton

**Files:**
- Create: `backend/settings.gradle`
- Create: `backend/build.gradle`
- Create: `backend/src/main/java/com/runvas/RunvasApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Modify: `backend/README.md`

- [ ] **Step 1: Write the project files**

Create `backend/settings.gradle`:

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'runvas-backend'
```

Create `backend/build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.7'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.runvas'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation 'org.postgresql:postgresql'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Create `backend/src/main/java/com/runvas/RunvasApplication.java`:

```java
package com.runvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RunvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunvasApplication.class, args);
    }
}
```

Create `backend/src/main/resources/application.yml`:

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: runvas-backend
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/runvas}
    username: ${DATABASE_USERNAME:runvas}
    password: ${DATABASE_PASSWORD:runvas}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true

runvas:
  jwt:
    secret: ${JWT_SECRET:dev-secret-dev-secret-dev-secret-dev-secret}
    expiration-seconds: ${JWT_EXPIRATION_SECONDS:3600}
  kakao:
    token-uri: ${KAKAO_TOKEN_URI:https://kauth.kakao.com/oauth/token}
    user-info-uri: ${KAKAO_USER_INFO_URI:https://kapi.kakao.com/v2/user/me}
    rest-api-key: ${KAKAO_REST_API_KEY:}
    client-secret: ${KAKAO_CLIENT_SECRET:}
```

Replace `backend/README.md` with:

```markdown
# Runvas Backend

Runvas 서버 API 구현을 관리하는 디렉토리입니다.

공통 API 기준, 데이터 모델, 좌표 규칙은 `../docs/` 문서를 기준으로 구현합니다.

## Stack

- Java 21
- Spring Boot 3.x
- Spring Web, Spring Security, Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- JUnit 5, MockMvc, Testcontainers

## Local Commands

```bash
./gradlew test
./gradlew bootRun
```

## Required Environment

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `KAKAO_REST_API_KEY`
- `KAKAO_CLIENT_SECRET`
```

- [ ] **Step 2: Run the skeleton test command**

Run:

```bash
cd backend
gradle test
```

Expected: Gradle downloads dependencies and reports `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add backend/settings.gradle backend/build.gradle backend/src/main/java/com/runvas/RunvasApplication.java backend/src/main/resources/application.yml backend/README.md
git commit -m "chore(backend): 스프링 부트 프로젝트 골격 추가"
```

---

### Task 2: Common Error Contract

**Files:**
- Create: `backend/src/main/java/com/runvas/global/error/ErrorCode.java`
- Create: `backend/src/main/java/com/runvas/global/error/ErrorResponse.java`
- Create: `backend/src/main/java/com/runvas/global/error/FieldErrorDetail.java`
- Create: `backend/src/main/java/com/runvas/global/error/RunvasException.java`
- Create: `backend/src/main/java/com/runvas/global/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/runvas/global/error/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/runvas/global/error/GlobalExceptionHandlerTest.java`:

```java
package com.runvas.global.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void validationErrorUsesDocumentedShape() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Invalid request body"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @RestController
    static class TestController {
        @PostMapping("/test/validation")
        void validate(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
gradle test --tests com.runvas.global.error.GlobalExceptionHandlerTest
```

Expected: FAIL because `GlobalExceptionHandler` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/runvas/global/error/ErrorCode.java`:

```java
package com.runvas.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request body"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access is forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with current state"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
```

Create `backend/src/main/java/com/runvas/global/error/FieldErrorDetail.java`:

```java
package com.runvas.global.error;

public record FieldErrorDetail(String field, String message) {
}
```

Create `backend/src/main/java/com/runvas/global/error/ErrorResponse.java`:

```java
package com.runvas.global.error;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(ErrorCode code, String message, List<FieldErrorDetail> details) {
        return new ErrorResponse(new ErrorBody(code.name(), message, details));
    }

    public record ErrorBody(String code, String message, List<FieldErrorDetail> details) {
    }
}
```

Create `backend/src/main/java/com/runvas/global/error/RunvasException.java`:

```java
package com.runvas.global.error;

public class RunvasException extends RuntimeException {

    private final ErrorCode errorCode;

    public RunvasException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public RunvasException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

Create `backend/src/main/java/com/runvas/global/error/GlobalExceptionHandler.java`:

```java
package com.runvas.global.error;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, code.defaultMessage(), details));
    }

    @ExceptionHandler(RunvasException.class)
    ResponseEntity<ErrorResponse> handleRunvas(RunvasException exception) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, exception.getMessage(), List.of()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
gradle test --tests com.runvas.global.error.GlobalExceptionHandlerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/global/error backend/src/test/java/com/runvas/global/error
git commit -m "feat(backend): 공통 에러 응답 형식 추가"
```

---

### Task 3: User Persistence

**Files:**
- Create: `backend/src/main/java/com/runvas/user/domain/AuthProvider.java`
- Create: `backend/src/main/java/com/runvas/user/domain/User.java`
- Create: `backend/src/main/java/com/runvas/user/repository/UserRepository.java`
- Create: `backend/src/main/java/com/runvas/user/dto/UserResponse.java`
- Create: `backend/src/main/resources/db/migration/V1__create_users.sql`
- Create: `backend/src/test/resources/application-test.yml`
- Test: `backend/src/test/java/com/runvas/user/repository/UserRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test**

Create `backend/src/test/resources/application-test.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```

Create `backend/src/test/java/com/runvas/user/repository/UserRepositoryTest.java`:

```java
package com.runvas.user.repository;

import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserRepository userRepository;

    @Test
    void findsUserByProviderAndProviderUserId() {
        User user = User.createKakaoUser(
                "12345",
                "runner@example.com",
                "Seoul Runner",
                null
        );
        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "12345");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("Seoul Runner");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
gradle test --tests com.runvas.user.repository.UserRepositoryTest
```

Expected: FAIL because user domain and repository classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/runvas/user/domain/AuthProvider.java`:

```java
package com.runvas.user.domain;

public enum AuthProvider {
    KAKAO
}
```

Create `backend/src/main/java/com/runvas/user/domain/User.java`:

```java
package com.runvas.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(length = 1000)
    private String profileImageUrl;

    @Column(length = 160)
    private String bio;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User createKakaoUser(String providerUserId, String email, String nickname, String profileImageUrl) {
        User user = new User();
        user.provider = AuthProvider.KAKAO;
        user.providerUserId = providerUserId;
        user.email = email;
        user.nickname = nickname == null || nickname.isBlank() ? "Runvas Runner" : nickname;
        user.profileImageUrl = profileImageUrl;
        user.bio = null;
        return user;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getBio() {
        return bio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

Create `backend/src/main/java/com/runvas/user/repository/UserRepository.java`:

```java
package com.runvas.user.repository;

import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
```

Create `backend/src/main/java/com/runvas/user/dto/UserResponse.java`:

```java
package com.runvas.user.dto;

import com.runvas.user.domain.User;

public record UserResponse(
        String id,
        String email,
        String provider,
        String nickname,
        String profileImageUrl,
        String bio,
        String createdAt,
        String updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                "user_" + user.getId(),
                user.getEmail(),
                user.getProvider().name(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBio(),
                user.getCreatedAt().toString(),
                user.getUpdatedAt().toString()
        );
    }
}
```

Create `backend/src/main/resources/db/migration/V1__create_users.sql`:

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    email VARCHAR(320),
    nickname VARCHAR(30) NOT NULL,
    profile_image_url VARCHAR(1000),
    bio VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_provider_provider_user_id UNIQUE (provider, provider_user_id)
);
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
gradle test --tests com.runvas.user.repository.UserRepositoryTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/user backend/src/main/resources/db/migration/V1__create_users.sql backend/src/test/resources/application-test.yml backend/src/test/java/com/runvas/user
git commit -m "feat(user): 카카오 사용자 저장 모델 추가"
```

---

### Task 4: JWT Provider and Security Configuration

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/service/JwtProvider.java`
- Create: `backend/src/main/java/com/runvas/global/security/RunvasPrincipal.java`
- Create: `backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/runvas/global/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java`

- [ ] **Step 1: Write the failing JWT test**

Create `backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java`:

```java
package com.runvas.auth.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    @Test
    void createsTokenContainingUserId() {
        JwtProvider jwtProvider = new JwtProvider("dev-secret-dev-secret-dev-secret-dev-secret", 3600);
        UUID userId = UUID.randomUUID();

        String token = jwtProvider.createAccessToken(userId);

        assertThat(jwtProvider.parseUserId(token)).isEqualTo(userId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
gradle test --tests com.runvas.auth.service.JwtProviderTest
```

Expected: FAIL because `JwtProvider` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/runvas/auth/service/JwtProvider.java`:

```java
package com.runvas.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtProvider(
            @Value("${runvas.jwt.secret}") String secret,
            @Value("${runvas.jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public UUID parseUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }
}
```

Create `backend/src/main/java/com/runvas/global/security/RunvasPrincipal.java`:

```java
package com.runvas.global.security;

import java.util.UUID;

public record RunvasPrincipal(UUID userId) {
}
```

Create `backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java`:

```java
package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length());
            UUID userId = jwtProvider.parseUserId(token);
            RunvasPrincipal principal = new RunvasPrincipal(userId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
```

Create `backend/src/main/java/com/runvas/global/security/SecurityConfig.java`:

```java
package com.runvas.global.security;

import com.runvas.auth.service.JwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/kakao").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
gradle test --tests com.runvas.auth.service.JwtProviderTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/auth/service/JwtProvider.java backend/src/main/java/com/runvas/global/security backend/src/test/java/com/runvas/auth/service/JwtProviderTest.java
git commit -m "feat(auth): JWT 인증 기반 추가"
```

---

### Task 5: Kakao Login Service and Endpoint

**Files:**
- Create: `backend/src/main/java/com/runvas/auth/dto/KakaoLoginRequest.java`
- Create: `backend/src/main/java/com/runvas/auth/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/runvas/auth/service/KakaoUserInfo.java`
- Create: `backend/src/main/java/com/runvas/auth/service/KakaoAuthClient.java`
- Create: `backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java`
- Create: `backend/src/main/java/com/runvas/auth/controller/AuthController.java`
- Test: `backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java`:

```java
package com.runvas.auth.controller;

import com.runvas.auth.service.KakaoAuthClient;
import com.runvas.auth.service.KakaoUserInfo;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
        registry.add("runvas.jwt.expiration-seconds", () -> "3600");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    KakaoAuthClient kakaoAuthClient;

    @Test
    void kakaoLoginCreatesUserAndReturnsRunvasToken() throws Exception {
        when(kakaoAuthClient.fetchUserInfo(anyString(), anyString()))
                .thenReturn(new KakaoUserInfo("kakao-123", "runner@example.com", "Seoul Runner", null));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "KAKAO",
                                  "authorizationCode": "auth-code",
                                  "redirectUri": "runvas://auth/kakao"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.id").isString())
                .andExpect(jsonPath("$.user.provider").value("KAKAO"))
                .andExpect(jsonPath("$.user.nickname").value("Seoul Runner"))
                .andExpect(jsonPath("$.user.providerUserId").doesNotExist())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
gradle test --tests com.runvas.auth.controller.AuthControllerTest
```

Expected: FAIL because auth controller/service DTOs do not exist.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/runvas/auth/dto/KakaoLoginRequest.java`:

```java
package com.runvas.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank String provider,
        @NotBlank String authorizationCode,
        @NotBlank String redirectUri
) {
}
```

Create `backend/src/main/java/com/runvas/auth/dto/AuthResponse.java`:

```java
package com.runvas.auth.dto;

import com.runvas.user.dto.UserResponse;

public record AuthResponse(String accessToken, UserResponse user, boolean isNewUser) {
}
```

Create `backend/src/main/java/com/runvas/auth/service/KakaoUserInfo.java`:

```java
package com.runvas.auth.service;

public record KakaoUserInfo(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
```

Create `backend/src/main/java/com/runvas/auth/service/KakaoAuthClient.java`:

```java
package com.runvas.auth.service;

public interface KakaoAuthClient {

    KakaoUserInfo fetchUserInfo(String authorizationCode, String redirectUri);
}
```

Create `backend/src/main/java/com/runvas/auth/service/KakaoAuthService.java`:

```java
package com.runvas.auth.service;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoAuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public KakaoAuthService(KakaoAuthClient kakaoAuthClient, UserRepository userRepository, JwtProvider jwtProvider) {
        this.kakaoAuthClient = kakaoAuthClient;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResponse login(KakaoLoginRequest request) {
        if (!"KAKAO".equals(request.provider())) {
            throw new RunvasException(ErrorCode.VALIDATION_ERROR, "provider must be KAKAO");
        }
        KakaoUserInfo kakaoUserInfo = kakaoAuthClient.fetchUserInfo(
                request.authorizationCode(),
                request.redirectUri()
        );
        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(
                AuthProvider.KAKAO,
                kakaoUserInfo.providerUserId()
        );
        boolean isNewUser = existingUser.isEmpty();
        User user = existingUser.orElseGet(() -> userRepository.save(User.createKakaoUser(
                        kakaoUserInfo.providerUserId(),
                        kakaoUserInfo.email(),
                        kakaoUserInfo.nickname(),
                        kakaoUserInfo.profileImageUrl()
                )));
        String accessToken = jwtProvider.createAccessToken(user.getId());
        return new AuthResponse(accessToken, UserResponse.from(user), isNewUser);
    }
}
```

Create `backend/src/main/java/com/runvas/auth/controller/AuthController.java`:

```java
package com.runvas.auth.controller;

import com.runvas.auth.dto.AuthResponse;
import com.runvas.auth.dto.KakaoLoginRequest;
import com.runvas.auth.service.KakaoAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KakaoAuthService kakaoAuthService;

    public AuthController(KakaoAuthService kakaoAuthService) {
        this.kakaoAuthService = kakaoAuthService;
    }

    @PostMapping("/kakao")
    AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return kakaoAuthService.login(request);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
gradle test --tests com.runvas.auth.controller.AuthControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/auth backend/src/test/java/com/runvas/auth/controller/AuthControllerTest.java
git commit -m "feat(auth): 카카오 로그인 API 추가"
```

---

### Task 6: Real Kakao HTTP Client

**Files:**
- Modify: `backend/src/main/java/com/runvas/RunvasApplication.java`
- Create: `backend/src/main/java/com/runvas/auth/service/KakaoHttpAuthClient.java`
- Test: `backend/src/test/java/com/runvas/auth/service/KakaoHttpAuthClientTest.java`

- [ ] **Step 1: Write the client parsing test**

Create `backend/src/test/java/com/runvas/auth/service/KakaoHttpAuthClientTest.java`:

```java
package com.runvas.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoHttpAuthClientTest {

    @Test
    void mapsKakaoUserPayload() {
        String json = """
                {
                  "id": 12345,
                  "kakao_account": {
                    "email": "runner@example.com",
                    "profile": {
                      "nickname": "Seoul Runner",
                      "profile_image_url": "https://example.com/profile.png"
                    }
                  }
                }
                """;

        KakaoUserInfo userInfo = KakaoHttpAuthClient.parseUserInfo(json);

        assertThat(userInfo.providerUserId()).isEqualTo("12345");
        assertThat(userInfo.email()).isEqualTo("runner@example.com");
        assertThat(userInfo.nickname()).isEqualTo("Seoul Runner");
        assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/profile.png");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
gradle test --tests com.runvas.auth.service.KakaoHttpAuthClientTest
```

Expected: FAIL because `KakaoHttpAuthClient` does not exist.

- [ ] **Step 3: Write minimal implementation**

Modify `backend/src/main/java/com/runvas/RunvasApplication.java`:

```java
package com.runvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class RunvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunvasApplication.class, args);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
```

Create `backend/src/main/java/com/runvas/auth/service/KakaoHttpAuthClient.java`:

```java
package com.runvas.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoHttpAuthClient implements KakaoAuthClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String tokenUri;
    private final String userInfoUri;
    private final String restApiKey;
    private final String clientSecret;

    public KakaoHttpAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${runvas.kakao.token-uri}") String tokenUri,
            @Value("${runvas.kakao.user-info-uri}") String userInfoUri,
            @Value("${runvas.kakao.rest-api-key}") String restApiKey,
            @Value("${runvas.kakao.client-secret}") String clientSecret
    ) {
        this.restClient = restClientBuilder.build();
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
    }

    @Override
    public KakaoUserInfo fetchUserInfo(String authorizationCode, String redirectUri) {
        String kakaoAccessToken = exchangeAccessToken(authorizationCode, redirectUri);
        String userInfoJson = restClient.get()
                .uri(userInfoUri)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(String.class);
        return parseUserInfo(userInfoJson);
    }

    private String exchangeAccessToken(String authorizationCode, String redirectUri) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        String tokenJson = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return parseAccessToken(tokenJson);
    }

    static String parseAccessToken(String json) {
        try {
            return OBJECT_MAPPER.readTree(json).path("access_token").asText();
        } catch (Exception exception) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED, "Kakao authentication failed");
        }
    }

    static KakaoUserInfo parseUserInfo(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode account = root.path("kakao_account");
            JsonNode profile = account.path("profile");
            return new KakaoUserInfo(
                    root.path("id").asText(),
                    textOrNull(account.path("email")),
                    textOrNull(profile.path("nickname")),
                    textOrNull(profile.path("profile_image_url"))
            );
        } catch (Exception exception) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED, "Kakao authentication failed");
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
gradle test --tests com.runvas.auth.service.KakaoHttpAuthClientTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/RunvasApplication.java backend/src/main/java/com/runvas/auth/service/KakaoHttpAuthClient.java backend/src/test/java/com/runvas/auth/service/KakaoHttpAuthClientTest.java
git commit -m "feat(auth): 카카오 서버 연동 클라이언트 추가"
```

---

### Task 7: Authenticated GET /api/me

**Files:**
- Create: `backend/src/main/java/com/runvas/user/controller/MeController.java`
- Create: `backend/src/main/java/com/runvas/user/dto/MeResponse.java`
- Test: `backend/src/test/java/com/runvas/user/controller/MeControllerTest.java`

- [ ] **Step 1: Write the failing controller test**

Create `backend/src/test/java/com/runvas/user/controller/MeControllerTest.java`:

```java
package com.runvas.user.controller;

import com.runvas.auth.service.JwtProvider;
import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MeControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("runvas.jwt.secret", () -> "dev-secret-dev-secret-dev-secret-dev-secret");
        registry.add("runvas.jwt.expiration-seconds", () -> "3600");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Test
    void returnsCurrentUser() throws Exception {
        User user = userRepository.saveAndFlush(User.createKakaoUser(
                "kakao-123",
                "runner@example.com",
                "Seoul Runner",
                null
        ));
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user_" + user.getId()))
                .andExpect(jsonPath("$.user.email").value("runner@example.com"))
                .andExpect(jsonPath("$.user.provider").value("KAKAO"))
                .andExpect(jsonPath("$.user.providerUserId").doesNotExist());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
gradle test --tests com.runvas.user.controller.MeControllerTest
```

Expected: FAIL because `MeController` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `backend/src/main/java/com/runvas/user/dto/MeResponse.java`:

```java
package com.runvas.user.dto;

public record MeResponse(UserResponse user) {
}
```

Create `backend/src/main/java/com/runvas/user/controller/MeController.java`:

```java
package com.runvas.user.controller;

import com.runvas.global.error.ErrorCode;
import com.runvas.global.error.RunvasException;
import com.runvas.global.security.RunvasPrincipal;
import com.runvas.user.dto.MeResponse;
import com.runvas.user.dto.UserResponse;
import com.runvas.user.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal RunvasPrincipal principal) {
        if (principal == null) {
            throw new RunvasException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(principal.userId())
                .map(user -> new MeResponse(UserResponse.from(user)))
                .orElseThrow(() -> new RunvasException(ErrorCode.UNAUTHORIZED));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
gradle test --tests com.runvas.user.controller.MeControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/runvas/user/controller backend/src/main/java/com/runvas/user/dto/MeResponse.java backend/src/test/java/com/runvas/user/controller/MeControllerTest.java
git commit -m "feat(user): 현재 사용자 조회 API 추가"
```

---

### Task 8: Full Verification and Documentation Check

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Run all backend tests**

Run:

```bash
cd backend
gradle test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm API contract alignment**

Check these points against `docs/api-contract.md`:

- `POST /api/auth/kakao` request fields are `provider`, `authorizationCode`, `redirectUri`.
- Response fields are exactly `accessToken`, `user`, `isNewUser`.
- `user.providerUserId` is absent.
- `GET /api/me` requires `Authorization: Bearer <accessToken>`.
- Validation errors use `error.code`, `error.message`, `error.details`.

- [ ] **Step 3: Update README with implemented endpoints**

Add this section to `backend/README.md`:

```markdown
## Implemented MVP APIs

- `POST /api/auth/kakao`
- `GET /api/me`

`POST /api/auth/kakao` exchanges a Kakao authorization code on the backend and returns a Runvas JWT.
The Kakao access token and provider user ID are never returned in API responses.
```

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs(backend): 인증 API 구현 범위 정리"
```

---

## Self-Review

Spec coverage:

- `docs/backend-architecture.md` Java 21, Spring Boot 3.x, Spring Security, Validation, JPA, PostgreSQL, Flyway, MockMvc, Testcontainers: covered in Tasks 1-3.
- `POST /api/auth/kakao`: covered in Task 5 and Task 6.
- Runvas JWT only, no Kakao token in response: covered in Task 5 response DTO and tests.
- `providerUserId` stored internally but not exposed: covered in Task 3 entity and Task 5/7 JSON assertions.
- `GET /api/me`: covered in Task 7.
- Common error response shape: covered in Task 2.

Placeholder scan:

- No forbidden placeholder phrases remain in implementation steps.
- Every code-changing step includes concrete file content or exact README text.

Type consistency:

- `User.getId()` returns `UUID`; JWT subject stores UUID; public response prefixes `user_`.
- `KakaoAuthClient.fetchUserInfo(String authorizationCode, String redirectUri)` is used by service and mocked in tests.
- `RunvasPrincipal(UUID userId)` is created by JWT filter and consumed by `MeController`.
