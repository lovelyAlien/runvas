# 운영자 관리자 대시보드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `backend/`에 운영자 전용 세션 로그인 기반의 읽기 전용 관리자 대시보드(회원/코스/게시글 통계
+ 최근 30일 추이 + 목록 조회)를 추가한다.

**Architecture:** Spring Boot 단일 애플리케이션 안에 `com.runvas.backend.admin` 패키지를 신설하고,
`/admin/**`를 매칭하는 별도 `SecurityFilterChain`(세션 폼 로그인)을 기존 `/api/**` JWT 체인과
나란히 둔다. 화면은 Thymeleaf 서버사이드 렌더링, 통계 추이 차트는 Chart.js CDN을 사용한다.

**Tech Stack:** Java 21, Spring Boot 3.3.7, Spring Security(폼 로그인 + BCrypt), Spring Data JPA,
Thymeleaf, PostgreSQL/Flyway, Chart.js(CDN), JUnit 5 + Mockito + Testcontainers.

## Global Constraints

- 관리 쓰기 액션(삭제/정지 등)은 만들지 않는다 — 모든 화면은 조회 전용.
- API 요청량/에러율/응답시간 계측(Actuator/Micrometer)은 이번 범위에 포함하지 않는다.
- 관리자 계정 셀프 서비스 생성/변경 UI는 만들지 않는다 — 최초 계정은 환경변수 시드로만 생성.
- 모바일 앱(`mobile/`)은 변경하지 않는다.
- `docs/api-contract.md`/`docs/data-model.md`/`docs/geo-conventions.md`/`docs/gpx-export.md`는
  변경하지 않는다 (모바일과 공유하는 계약이 아님).
- 커밋 메시지에 `Co-Authored-By` 등 도구/저작자 표시를 넣지 않는다. 커밋 전 `sh scripts/setup-git-hooks.sh`가
  이 워크트리(`/Users/lovelyalien/Documents/workspace/runvas/.claude/worktrees/admin-dashboard`)에서
  이미 실행되어 있어야 한다 (완료 상태, 재실행 불필요).
- 모든 커밋은 이 워크트리 안에서, 브랜치 `docs/admin-dashboard-design` 위에서 만든다. `git add`는
  파일을 명시해서 추가하고 `git add -A`/`git add .`는 쓰지 않는다.
- 참고 스펙: `docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`.

---

## Task 1: Thymeleaf 의존성 추가

**Files:**
- Modify: `backend/build.gradle`

**Interfaces:**
- Produces: 이후 모든 태스크가 사용할 `spring-boot-starter-thymeleaf` (뷰 리졸버, `templates/` 자동 인식).

- [ ] **Step 1: build.gradle에 의존성 추가**

`backend/build.gradle`의 `dependencies` 블록에서 아래 줄을 찾는다.

```gradle
    implementation 'org.springframework.boot:spring-boot-starter-web'
```

바로 아래 줄에 추가한다.

```gradle
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add backend/build.gradle
git commit -m "chore(backend): Thymeleaf 의존성 추가"
```

---

## Task 2: AdminAccount 엔티티 + 마이그레이션 + 리포지토리

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__create_admin_accounts.sql`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminAccount.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminAccountRepository.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminAccountRepositoryTest.java`

**Interfaces:**
- Produces: `AdminAccount(String username, String passwordHash)` 생성자, `getId()`, `getUsername()`,
  `getPasswordHash()`, `getCreatedAt()`, `getLastLoginAt()`, `recordLogin()`.
  `AdminAccountRepository extends JpaRepository<AdminAccount, String>`,
  `Optional<AdminAccount> findByUsername(String username)`.

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminAccountRepositoryTest.java`:

```java
package com.runvas.backend.admin;

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
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminAccountRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AdminAccountRepository adminAccountRepository;

    @Test
    void savesAndFindsAccountByUsername() {
        adminAccountRepository.saveAndFlush(new AdminAccount("operator", "hashed-value"));

        Optional<AdminAccount> found = adminAccountRepository.findByUsername("operator");

        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isEqualTo("hashed-value");
        assertThat(found.get().getLastLoginAt()).isNull();
    }

    @Test
    void returnsEmptyWhenUsernameNotFound() {
        Optional<AdminAccount> found = adminAccountRepository.findByUsername("missing");

        assertThat(found).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminAccountRepositoryTest"`
Expected: FAIL — `AdminAccount`/`AdminAccountRepository` 클래스를 찾을 수 없다는 컴파일 에러.

- [ ] **Step 3: Flyway 마이그레이션 작성**

`backend/src/main/resources/db/migration/V14__create_admin_accounts.sql`:

```sql
CREATE TABLE admin_accounts (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_admin_accounts_username ON admin_accounts (username);
```

- [ ] **Step 4: AdminAccount 엔티티 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminAccount.java`:

```java
package com.runvas.backend.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 운영자 전용 관리자 대시보드 로그인 계정. Runvas 사용자(카카오 로그인) 계정과는 완전히 별개다.
// docs/superpowers/specs/2026-07-21-admin-dashboard-design.md "인증 설계" 참고.
@Entity
@Table(name = "admin_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastLoginAt;

    public AdminAccount(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }
}
```

- [ ] **Step 5: AdminAccountRepository 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminAccountRepository.java`:

```java
package com.runvas.backend.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, String> {

    Optional<AdminAccount> findByUsername(String username);
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminAccountRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed (Docker 데몬이 떠 있어야 Testcontainers가 동작한다).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V14__create_admin_accounts.sql \
        backend/src/main/java/com/runvas/backend/admin/AdminAccount.java \
        backend/src/main/java/com/runvas/backend/admin/AdminAccountRepository.java \
        backend/src/test/java/com/runvas/backend/admin/AdminAccountRepositoryTest.java
git commit -m "feat(admin): AdminAccount 엔티티/리포지토리 추가"
```

---

## Task 3: 초기 관리자 계정 시드

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminAccountSeeder.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminAccountSeederTest.java`

**Interfaces:**
- Consumes: `AdminAccountRepository`(Task 2), `org.springframework.security.crypto.password.PasswordEncoder`
  (인터페이스만 사용 — 구현 빈은 Task 4에서 등록됨. 이 태스크의 테스트는 Spring 컨텍스트 없이
  Mockito로만 검증하므로 빈 존재 여부와 무관하다).
- Produces: `AdminAccountSeeder(AdminAccountRepository, PasswordEncoder, String seedUsername, String seedPassword)`
  생성자와 `run(ApplicationArguments)` 메서드.

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminAccountSeederTest.java`:

```java
package com.runvas.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountSeederTest {

    private final AdminAccountRepository adminAccountRepository = mock(AdminAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void seedsAccountWhenCredentialsPresentAndTableEmpty() {
        when(adminAccountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");
        AdminAccountSeeder seeder =
                new AdminAccountSeeder(adminAccountRepository, passwordEncoder, "operator", "secret");

        seeder.run(null);

        verify(adminAccountRepository, times(1)).save(any(AdminAccount.class));
    }

    @Test
    void doesNothingWhenSeedCredentialsMissing() {
        AdminAccountSeeder seeder =
                new AdminAccountSeeder(adminAccountRepository, passwordEncoder, "", "");

        seeder.run(null);

        verify(adminAccountRepository, never()).save(any(AdminAccount.class));
    }

    @Test
    void doesNothingWhenAccountsAlreadyExist() {
        when(adminAccountRepository.count()).thenReturn(1L);
        AdminAccountSeeder seeder =
                new AdminAccountSeeder(adminAccountRepository, passwordEncoder, "operator", "secret");

        seeder.run(null);

        verify(adminAccountRepository, never()).save(any(AdminAccount.class));
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminAccountSeederTest"`
Expected: FAIL — `AdminAccountSeeder` 클래스가 없다는 컴파일 에러.

- [ ] **Step 3: AdminAccountSeeder 구현**

`backend/src/main/java/com/runvas/backend/admin/AdminAccountSeeder.java`:

```java
package com.runvas.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 최초 배포 시 admin_accounts가 비어 있으면 환경변수로 계정 1건을 부트스트랩한다.
// docs/superpowers/specs/2026-07-21-admin-dashboard-design.md "초기 계정 부트스트랩" 참고.
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedUsername;
    private final String seedPassword;

    public AdminAccountSeeder(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${runvas.admin.seed-username:}") String seedUsername,
            @Value("${runvas.admin.seed-password:}") String seedPassword) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedUsername = seedUsername;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedUsername.isBlank() || seedPassword.isBlank()) {
            return;
        }
        if (adminAccountRepository.count() > 0) {
            return;
        }
        adminAccountRepository.save(new AdminAccount(seedUsername, passwordEncoder.encode(seedPassword)));
        log.info("Seeded initial admin account: {}", seedUsername);
    }
}
```

- [ ] **Step 4: application.yml에 환경변수 매핑 추가**

`backend/src/main/resources/application.yml`에서 아래 블록을 찾는다.

```yaml
  upload:
    dir: ${UPLOAD_DIR:./uploads}
    base-url: ${UPLOAD_BASE_URL:http://localhost:8080}
```

바로 아래에 추가한다.

```yaml
  upload:
    dir: ${UPLOAD_DIR:./uploads}
    base-url: ${UPLOAD_BASE_URL:http://localhost:8080}
  admin:
    seed-username: ${ADMIN_SEED_USERNAME:}
    seed-password: ${ADMIN_SEED_PASSWORD:}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminAccountSeederTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminAccountSeeder.java \
        backend/src/main/resources/application.yml \
        backend/src/test/java/com/runvas/backend/admin/AdminAccountSeederTest.java
git commit -m "feat(admin): 초기 관리자 계정 환경변수 시드 추가"
```

---

## Task 4: 관리자 전용 세션 로그인 보안 체인

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminUserDetailsService.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminSecurityConfig.java`
- Modify: `backend/src/main/java/com/runvas/global/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminSecurityConfigTest.java`

**Interfaces:**
- Consumes: `AdminAccountRepository`(Task 2) — `findByUsername`, `AdminAccount.recordLogin()`.
- Produces: `PasswordEncoder` 빈(`BCryptPasswordEncoder`) — 이후 Task 3의 `AdminAccountSeeder`가
  런타임에 실제로 주입받는 구현체. `/admin/**` 경로가 세션 로그인으로 보호됨. 로그인 성공 시
  `/admin/dashboard`로 리다이렉트(아직 해당 컨트롤러는 없음 — Task 8에서 추가), 실패 시
  `/admin/login?error`로 리다이렉트.

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminSecurityConfigTest.java`:

```java
package com.runvas.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminSecurityConfigTest {

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
    AdminAccountRepository adminAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void unauthenticatedRequestRedirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    @Test
    void loginWithWrongPasswordRedirectsBackToLoginWithError() throws Exception {
        adminAccountRepository.saveAndFlush(new AdminAccount("operator", passwordEncoder.encode("correct-password")));

        mockMvc.perform(post("/admin/login")
                        .param("username", "operator")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void loginWithCorrectCredentialsRedirectsToDashboardAndRecordsLastLoginAt() throws Exception {
        adminAccountRepository.saveAndFlush(new AdminAccount("operator", passwordEncoder.encode("correct-password")));

        mockMvc.perform(post("/admin/login")
                        .param("username", "operator")
                        .param("password", "correct-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));

        assertThat(adminAccountRepository.findByUsername("operator").orElseThrow().getLastLoginAt()).isNotNull();
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminSecurityConfigTest"`
Expected: FAIL — `/admin/dashboard`가 아직 어떤 필터체인에도 매칭되지 않아 스프링 시큐리티 기본
동작(전부 `authenticated()` 걸린 기존 체인)이나 404 등 기대와 다른 응답이 온다.

- [ ] **Step 3: AdminUserDetailsService 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminUserDetailsService.java`:

```java
package com.runvas.backend.admin;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccountRepository;

    public AdminUserDetailsService(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AdminAccount account = adminAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown admin account: " + username));
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities("ROLE_ADMIN")
                .build();
    }
}
```

- [ ] **Step 4: AdminSecurityConfig 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminSecurityConfig.java`:

```java
package com.runvas.backend.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class AdminSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DaoAuthenticationProvider adminAuthenticationProvider(
            AdminUserDetailsService adminUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationSuccessHandler adminLoginSuccessHandler(AdminAccountRepository adminAccountRepository) {
        return (request, response, authentication) -> {
            adminAccountRepository.findByUsername(authentication.getName())
                    .ifPresent(account -> {
                        account.recordLogin();
                        adminAccountRepository.save(account);
                    });
            response.sendRedirect(request.getContextPath() + "/admin/dashboard");
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider adminAuthenticationProvider,
            AuthenticationSuccessHandler adminLoginSuccessHandler) throws Exception {
        return http
                .securityMatcher("/admin/**")
                .authenticationProvider(adminAuthenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(adminLoginSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login")
                        .permitAll()
                )
                .build();
    }
}
```

- [ ] **Step 5: 기존 SecurityConfig에 매칭 범위와 순서 명시**

`backend/src/main/java/com/runvas/global/security/SecurityConfig.java`에서 import 블록을 찾는다.

```java
import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
```

아래처럼 `Order` import를 추가한다.

```java
import com.runvas.auth.service.JwtProvider;
import com.runvas.auth.service.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
```

다음으로 빈 선언부를 찾는다.

```java
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
```

아래처럼 `@Order(2)` 애노테이션을 추가한다.

```java
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
```

마지막으로 체인 빌더 시작부를 찾는다.

```java
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
```

`/admin/**`와 겹치지 않도록 이 체인이 담당하는 범위를 명시한다.

```java
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminSecurityConfigTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 7: 기존 JWT 인증 테스트가 여전히 통과하는지 확인 (회귀 확인)**

Run: `cd backend && ./gradlew test --tests "com.runvas.global.security.SecurityConfigTest" --tests "com.runvas.backend.course.CourseControllerContractTest"`
Expected: `BUILD SUCCESSFUL` — `/api/**` 범위 명시 후에도 기존 JWT 인증 동작이 그대로 유지된다.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminUserDetailsService.java \
        backend/src/main/java/com/runvas/backend/admin/AdminSecurityConfig.java \
        backend/src/main/java/com/runvas/global/security/SecurityConfig.java \
        backend/src/test/java/com/runvas/backend/admin/AdminSecurityConfigTest.java
git commit -m "feat(admin): /admin 전용 세션 로그인 보안 체인 추가"
```

---

## Task 5: 로그인 화면

**Files:**
- Create: `backend/src/main/resources/templates/admin/login.html`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminLoginController.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminLoginControllerTest.java`

**Interfaces:**
- Consumes: Task 4에서 만든 `/admin/login` 보안 설정(`permitAll`, `loginProcessingUrl`).
- Produces: `GET /admin/login` → 뷰 `admin/login` 렌더링.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminLoginControllerTest.java`:

```java
package com.runvas.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminLoginControllerTest {

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

    @Test
    void loginPageRendersWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Runvas 관리자 로그인")));
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminLoginControllerTest"`
Expected: FAIL — 뷰가 없어 404 또는 템플릿 리졸브 에러.

- [ ] **Step 3: 로그인 템플릿 작성**

`backend/src/main/resources/templates/admin/login.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>Runvas 관리자 로그인</title>
</head>
<body>
<h1>Runvas 관리자 로그인</h1>
<p th:if="${param.error}" style="color:red;">아이디 또는 비밀번호가 올바르지 않습니다.</p>
<form method="post" th:action="@{/admin/login}">
    <label>아이디 <input type="text" name="username"/></label>
    <label>비밀번호 <input type="password" name="password"/></label>
    <button type="submit">로그인</button>
</form>
</body>
</html>
```

- [ ] **Step 4: AdminLoginController 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminLoginController.java`:

```java
package com.runvas.backend.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminLoginController {

    @GetMapping("/admin/login")
    String loginPage() {
        return "admin/login";
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminLoginControllerTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/templates/admin/login.html \
        backend/src/main/java/com/runvas/backend/admin/AdminLoginController.java \
        backend/src/test/java/com/runvas/backend/admin/AdminLoginControllerTest.java
git commit -m "feat(admin): 로그인 화면 추가"
```

---

## Task 6: 조회 전용 리포지토리 추가 (회원/코스/게시글)

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/DailyCountProjection.java`
- Modify: `backend/src/main/java/com/runvas/user/repository/UserRepository.java`
- Modify: `backend/src/main/java/com/runvas/backend/course/CourseRepository.java`
- Modify: `backend/src/main/java/com/runvas/backend/community/PostRepository.java`
- Modify: `backend/src/test/java/com/runvas/user/repository/UserRepositoryTest.java`
- Create: `backend/src/test/java/com/runvas/backend/course/CourseRepositoryTest.java`
- Modify: `backend/src/test/java/com/runvas/backend/community/PostRepositoryTest.java`

**Interfaces:**
- Produces: `DailyCountProjection { LocalDate getDay(); long getCnt(); }` — Task 7의
  `AdminStatsService`가 세 리포지토리의 `countDailySince(Instant)` 결과 타입으로 사용한다.
  `UserRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(String, String, Pageable): Page<User>`,
  `UserRepository.countDailySince(Instant): List<DailyCountProjection>`,
  `CourseRepository.countByVisibility(CourseVisibility): long`,
  `CourseRepository.findByTitleContainingIgnoreCase(String, Pageable): Page<Course>`,
  `CourseRepository.findByTitleContainingIgnoreCaseAndVisibility(String, CourseVisibility, Pageable): Page<Course>`,
  `CourseRepository.countDailySince(Instant): List<DailyCountProjection>`,
  `PostRepository.findByTitleContainingIgnoreCase(String, Pageable): Page<Post>`,
  `PostRepository.countDailySince(Instant): List<DailyCountProjection>`.

- [ ] **Step 1: DailyCountProjection 작성**

`backend/src/main/java/com/runvas/backend/admin/DailyCountProjection.java`:

```java
package com.runvas.backend.admin;

import java.time.LocalDate;

public interface DailyCountProjection {

    LocalDate getDay();

    long getCnt();
}
```

- [ ] **Step 2: 실패하는 UserRepository 테스트 추가**

`backend/src/test/java/com/runvas/user/repository/UserRepositoryTest.java`의 마지막 `}` 바로 위에
아래 테스트 메서드를 추가한다.

```java
    @Test
    void searchFindsUserByNicknameOrEmailIgnoringCase() {
        userRepository.saveAndFlush(User.createKakaoUser("k1", "seoul@example.com", "서울러너", null));
        userRepository.saveAndFlush(User.createKakaoUser("k2", "busan@example.com", "부산러너", null));

        org.springframework.data.domain.Page<User> found =
                userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "서울", "서울", org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getNickname()).isEqualTo("서울러너");
    }

    @Test
    void countDailySinceGroupsUsersByCreationDate() {
        userRepository.saveAndFlush(User.createKakaoUser("k3", "a@example.com", "가입자A", null));
        userRepository.saveAndFlush(User.createKakaoUser("k4", "b@example.com", "가입자B", null));

        java.util.List<DailyCountProjection> counts =
                userRepository.countDailySince(java.time.Instant.now().minusSeconds(3600));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getCnt()).isEqualTo(2L);
    }
```

파일 상단 import 블록에 아래 두 줄을 추가한다 (기존 `import com.runvas.user.domain.User;` 아래).

```java
import com.runvas.backend.admin.DailyCountProjection;
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.repository.UserRepositoryTest"`
Expected: FAIL — `UserRepository`에 해당 메서드가 없다는 컴파일 에러.

- [ ] **Step 4: UserRepository에 메서드 추가**

`backend/src/main/java/com/runvas/user/repository/UserRepository.java` 전체를 아래로 교체한다.

```java
package com.runvas.user.repository;

import com.runvas.backend.admin.DailyCountProjection;
import com.runvas.user.domain.AuthProvider;
import com.runvas.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    List<User> findByDeletedAtLessThanEqual(Instant threshold);

    Page<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String nickname, String email, Pageable pageable);

    @Query("select cast(u.createdAt as date) as day, count(u) as cnt from User u "
            + "where u.createdAt >= :since group by cast(u.createdAt as date)")
    List<DailyCountProjection> countDailySince(@Param("since") Instant since);
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.user.repository.UserRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: 실패하는 CourseRepository 테스트 작성**

`backend/src/test/java/com/runvas/backend/course/CourseRepositoryTest.java`:

```java
package com.runvas.backend.course;

import com.runvas.backend.admin.DailyCountProjection;
import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CourseRepository courseRepository;

    private Course course(String title, CourseVisibility visibility) {
        RoutePoint point = new RoutePoint(37.5665, 126.978, 0);
        GeoBounds bounds = new GeoBounds(new GeoPoint(37.5665, 126.978), new GeoPoint(37.567, 126.979));
        return new Course(
                "author-1", title, null, List.of(point), List.of(point),
                1000, 600, bounds, visibility, Set.of());
    }

    @Test
    void countByVisibilityCountsOnlyMatchingCourses() {
        courseRepository.saveAndFlush(course("공개 코스", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("비공개 코스", CourseVisibility.PRIVATE));

        assertThat(courseRepository.countByVisibility(CourseVisibility.PUBLIC)).isEqualTo(1L);
        assertThat(courseRepository.countByVisibility(CourseVisibility.PRIVATE)).isEqualTo(1L);
    }

    @Test
    void searchByTitleAndVisibilityFiltersBoth() {
        courseRepository.saveAndFlush(course("한강 코스", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("한강 비공개 코스", CourseVisibility.PRIVATE));

        Page<Course> found = courseRepository.findByTitleContainingIgnoreCaseAndVisibility(
                "한강", CourseVisibility.PUBLIC, PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getTitle()).isEqualTo("한강 코스");
    }

    @Test
    void countDailySinceGroupsCoursesByCreationDate() {
        courseRepository.saveAndFlush(course("코스A", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("코스B", CourseVisibility.PUBLIC));

        List<DailyCountProjection> counts = courseRepository.countDailySince(Instant.now().minusSeconds(3600));

        assertThat(counts).hasSize(1);
        assertThat(counts.get(0).getCnt()).isEqualTo(2L);
    }
}
```

- [ ] **Step 7: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.course.CourseRepositoryTest"`
Expected: FAIL — `CourseRepository`에 해당 메서드가 없다는 컴파일 에러.

- [ ] **Step 8: CourseRepository에 메서드 추가**

`backend/src/main/java/com/runvas/backend/course/CourseRepository.java` 전체를 아래로 교체한다.

```java
package com.runvas.backend.course;

import com.runvas.backend.admin.DailyCountProjection;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, String> {

	// MVP 범위: bounds 겹침 + visibility=PUBLIC만 필터링하고 커서/태그/검색어는 서비스 레이어에서
	// 메모리상으로 추가 필터링한다 (실데이터 규모가 커지면 쿼리로 옮긴다 — design.md 참고).
	@Query(
			"select c from Course c where c.visibility = 'PUBLIC' "
					+ "and c.swLat <= :neLat and c.neLat >= :swLat "
					+ "and c.swLng <= :neLng and c.neLng >= :swLng "
					+ "order by c.createdAt desc")
	java.util.List<Course> findPublicCoursesWithinBounds(
			double swLat, double swLng, double neLat, double neLng);

	// bounds 없이 제목 부분 일치 검색 — 코스 이름 검색 기능용
	@Query("select c from Course c where c.visibility = 'PUBLIC' and lower(c.title) like lower(concat('%', :q, '%')) order by c.createdAt desc")
	java.util.List<Course> findPublicCoursesByTitle(@org.springframework.data.repository.query.Param("q") String q);

	// bounds 없이 태그 정확 일치 검색 (대소문자 구분 없음) — 태그 검색 기능용
	@Query("select distinct c from Course c join c.tags t where c.visibility = 'PUBLIC' and lower(t) = lower(:tag) order by c.createdAt desc")
	java.util.List<Course> findPublicCoursesByTag(@org.springframework.data.repository.query.Param("tag") String tag);

	// 본인이 만든 코스 목록 — visibility 필터 없이 PRIVATE도 포함한다.
	java.util.List<Course> findByAuthorIdOrderByCreatedAtDesc(String authorId);

	// 아래부터는 관리자 대시보드 전용 조회 (docs/superpowers/specs/2026-07-21-admin-dashboard-design.md).
	long countByVisibility(CourseVisibility visibility);

	Page<Course> findByTitleContainingIgnoreCase(String title, Pageable pageable);

	Page<Course> findByTitleContainingIgnoreCaseAndVisibility(
			String title, CourseVisibility visibility, Pageable pageable);

	@Query("select cast(c.createdAt as date) as day, count(c) as cnt from Course c "
			+ "where c.createdAt >= :since group by cast(c.createdAt as date)")
	java.util.List<DailyCountProjection> countDailySince(@Param("since") Instant since);
}
```

- [ ] **Step 9: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.course.CourseRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 10: 실패하는 PostRepository 테스트 추가**

`backend/src/test/java/com/runvas/backend/community/PostRepositoryTest.java`의 마지막 `}` 바로
위에 아래 테스트 메서드를 추가한다.

```java
	@Test
	void searchByTitleFiltersCaseInsensitively() {
		postRepository.saveAndFlush(new Post("author-1", "한강 러닝 후기", "본문", null, Set.of()));
		postRepository.saveAndFlush(new Post("author-1", "남산 등산 후기", "본문", null, Set.of()));

		org.springframework.data.domain.Page<Post> found = postRepository.findByTitleContainingIgnoreCase(
				"한강", org.springframework.data.domain.PageRequest.of(0, 20));

		assertThat(found.getContent()).hasSize(1);
		assertThat(found.getContent().get(0).getTitle()).isEqualTo("한강 러닝 후기");
	}

	@Test
	void countDailySinceGroupsPostsByCreationDate() {
		postRepository.saveAndFlush(new Post("author-1", "글1", "본문", null, Set.of()));
		postRepository.saveAndFlush(new Post("author-1", "글2", "본문", null, Set.of()));

		java.util.List<com.runvas.backend.admin.DailyCountProjection> counts =
				postRepository.countDailySince(java.time.Instant.now().minusSeconds(3600));

		assertThat(counts).hasSize(1);
		assertThat(counts.get(0).getCnt()).isEqualTo(2L);
	}
```

- [ ] **Step 11: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostRepositoryTest"`
Expected: FAIL — `PostRepository`에 해당 메서드가 없다는 컴파일 에러.

- [ ] **Step 12: PostRepository에 메서드 추가**

`backend/src/main/java/com/runvas/backend/community/PostRepository.java` 전체를 아래로 교체한다.

```java
package com.runvas.backend.community;

import com.runvas.backend.admin.DailyCountProjection;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, String> {
	List<Post> findAllByOrderByCreatedAtDesc();

	// 관리자 대시보드 전용 조회 (docs/superpowers/specs/2026-07-21-admin-dashboard-design.md).
	Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

	@Query("select cast(p.createdAt as date) as day, count(p) as cnt from Post p "
			+ "where p.createdAt >= :since group by cast(p.createdAt as date)")
	List<DailyCountProjection> countDailySince(@Param("since") Instant since);
}
```

- [ ] **Step 13: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.community.PostRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 14: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/DailyCountProjection.java \
        backend/src/main/java/com/runvas/user/repository/UserRepository.java \
        backend/src/main/java/com/runvas/backend/course/CourseRepository.java \
        backend/src/main/java/com/runvas/backend/community/PostRepository.java \
        backend/src/test/java/com/runvas/user/repository/UserRepositoryTest.java \
        backend/src/test/java/com/runvas/backend/course/CourseRepositoryTest.java \
        backend/src/test/java/com/runvas/backend/community/PostRepositoryTest.java
git commit -m "feat(admin): 회원/코스/게시글 관리자 조회 쿼리 추가"
```

---

## Task 7: 통계 집계 서비스

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminSummary.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/DailyTrendPoint.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminStatsService.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminStatsServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository.count()/countDailySince(Instant)`,
  `CourseRepository.countByVisibility(CourseVisibility)/countDailySince(Instant)`,
  `PostRepository.count()/countDailySince(Instant)`, `CommentRepository.count()`(JpaRepository 상속),
  `CourseCommentRepository.count()`(JpaRepository 상속) — 모두 Task 6에서 준비됨.
- Produces: `AdminSummary(long totalUsers, long publicCourses, long privateCourses, long totalPosts, long totalComments)`,
  `DailyTrendPoint(LocalDate day, long count)`,
  `AdminStatsService.summary(): AdminSummary`,
  `AdminStatsService.userSignupTrend(): List<DailyTrendPoint>`(30건, 오늘 포함, 빈 날짜는 0),
  `AdminStatsService.courseCreationTrend(): List<DailyTrendPoint>`,
  `AdminStatsService.postCreationTrend(): List<DailyTrendPoint>` — Task 8의 `AdminDashboardController`가 사용.

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminStatsServiceTest.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.CommentRepository;
import com.runvas.backend.community.CourseCommentRepository;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminStatsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final CourseCommentRepository courseCommentRepository = mock(CourseCommentRepository.class);
    private final AdminStatsService adminStatsService = new AdminStatsService(
            userRepository, courseRepository, postRepository, commentRepository, courseCommentRepository);

    @Test
    void summaryCombinesCountsFromAllRepositories() {
        when(userRepository.count()).thenReturn(10L);
        when(courseRepository.countByVisibility(CourseVisibility.PUBLIC)).thenReturn(4L);
        when(courseRepository.countByVisibility(CourseVisibility.PRIVATE)).thenReturn(6L);
        when(postRepository.count()).thenReturn(3L);
        when(commentRepository.count()).thenReturn(2L);
        when(courseCommentRepository.count()).thenReturn(5L);

        AdminSummary summary = adminStatsService.summary();

        assertThat(summary.totalUsers()).isEqualTo(10L);
        assertThat(summary.publicCourses()).isEqualTo(4L);
        assertThat(summary.privateCourses()).isEqualTo(6L);
        assertThat(summary.totalPosts()).isEqualTo(3L);
        assertThat(summary.totalComments()).isEqualTo(7L);
    }

    @Test
    void userSignupTrendFillsMissingDaysWithZeroAndCoversThirtyDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(userRepository.countDailySince(any(Instant.class)))
                .thenReturn(List.of(projection(today, 3L)));

        List<DailyTrendPoint> trend = adminStatsService.userSignupTrend();

        assertThat(trend).hasSize(30);
        assertThat(trend.get(29).day()).isEqualTo(today);
        assertThat(trend.get(29).count()).isEqualTo(3L);
        assertThat(trend.get(0).count()).isEqualTo(0L);
    }

    private DailyCountProjection projection(LocalDate day, long cnt) {
        return new DailyCountProjection() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public long getCnt() {
                return cnt;
            }
        };
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminStatsServiceTest"`
Expected: FAIL — `AdminSummary`/`DailyTrendPoint`/`AdminStatsService` 클래스가 없다는 컴파일 에러.

- [ ] **Step 3: AdminSummary/DailyTrendPoint 레코드 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminSummary.java`:

```java
package com.runvas.backend.admin;

public record AdminSummary(
        long totalUsers,
        long publicCourses,
        long privateCourses,
        long totalPosts,
        long totalComments) {
}
```

`backend/src/main/java/com/runvas/backend/admin/DailyTrendPoint.java`:

```java
package com.runvas.backend.admin;

import java.time.LocalDate;

public record DailyTrendPoint(LocalDate day, long count) {
}
```

- [ ] **Step 4: AdminStatsService 구현**

`backend/src/main/java/com/runvas/backend/admin/AdminStatsService.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.CommentRepository;
import com.runvas.backend.community.CourseCommentRepository;
import com.runvas.backend.community.PostRepository;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import com.runvas.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminStatsService {

    private static final int TREND_DAYS = 30;

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CourseCommentRepository courseCommentRepository;

    public AdminStatsService(
            UserRepository userRepository,
            CourseRepository courseRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            CourseCommentRepository courseCommentRepository) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.courseCommentRepository = courseCommentRepository;
    }

    public AdminSummary summary() {
        long totalUsers = userRepository.count();
        long publicCourses = courseRepository.countByVisibility(CourseVisibility.PUBLIC);
        long privateCourses = courseRepository.countByVisibility(CourseVisibility.PRIVATE);
        long totalPosts = postRepository.count();
        long totalComments = commentRepository.count() + courseCommentRepository.count();
        return new AdminSummary(totalUsers, publicCourses, privateCourses, totalPosts, totalComments);
    }

    public List<DailyTrendPoint> userSignupTrend() {
        return fillDailyTrend(userRepository.countDailySince(since()));
    }

    public List<DailyTrendPoint> courseCreationTrend() {
        return fillDailyTrend(courseRepository.countDailySince(since()));
    }

    public List<DailyTrendPoint> postCreationTrend() {
        return fillDailyTrend(postRepository.countDailySince(since()));
    }

    private Instant since() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(TREND_DAYS - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private List<DailyTrendPoint> fillDailyTrend(List<DailyCountProjection> rows) {
        Map<LocalDate, Long> byDay = rows.stream()
                .collect(Collectors.toMap(DailyCountProjection::getDay, DailyCountProjection::getCnt));
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(TREND_DAYS - 1L);
        List<DailyTrendPoint> points = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            points.add(new DailyTrendPoint(day, byDay.getOrDefault(day, 0L)));
        }
        return points;
    }
}
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminStatsServiceTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminSummary.java \
        backend/src/main/java/com/runvas/backend/admin/DailyTrendPoint.java \
        backend/src/main/java/com/runvas/backend/admin/AdminStatsService.java \
        backend/src/test/java/com/runvas/backend/admin/AdminStatsServiceTest.java
git commit -m "feat(admin): 통계 집계 서비스 추가"
```

---

## Task 8: 대시보드 화면

**Files:**
- Create: `backend/src/main/resources/templates/admin/fragments/nav.html`
- Create: `backend/src/main/resources/templates/admin/dashboard.html`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminDashboardController.java`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminDashboardControllerTest.java`

**Interfaces:**
- Consumes: `AdminStatsService`(Task 7).
- Produces: `GET /admin/dashboard` → 뷰 `admin/dashboard`. `admin/fragments/nav.html`의
  `nav` 프래그먼트는 Task 9~11의 목록 화면에서도 재사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminDashboardControllerTest.java`:

```java
package com.runvas.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminDashboardControllerTest {

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

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void dashboardRendersSummaryForAuthenticatedAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("대시보드")));
    }

    @Test
    void dashboardRedirectsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminDashboardControllerTest"`
Expected: FAIL — 컨트롤러/뷰가 없어 404.

- [ ] **Step 3: nav 프래그먼트 작성**

`backend/src/main/resources/templates/admin/fragments/nav.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<nav th:fragment="nav">
    <a th:href="@{/admin/dashboard}">대시보드</a>
    <a th:href="@{/admin/users}">회원</a>
    <a th:href="@{/admin/courses}">코스</a>
    <a th:href="@{/admin/posts}">게시글</a>
    <form method="post" th:action="@{/admin/logout}" style="display:inline;">
        <button type="submit">로그아웃</button>
    </form>
</nav>
</body>
</html>
```

- [ ] **Step 4: 대시보드 템플릿 작성**

`backend/src/main/resources/templates/admin/dashboard.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>Runvas 관리자 대시보드</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"></script>
</head>
<body>
<nav th:replace="~{admin/fragments/nav :: nav}"></nav>
<h1>대시보드</h1>
<ul>
    <li>전체 회원 수: <span th:text="${summary.totalUsers()}"></span></li>
    <li>공개 코스 수: <span th:text="${summary.publicCourses()}"></span></li>
    <li>비공개 코스 수: <span th:text="${summary.privateCourses()}"></span></li>
    <li>전체 게시글 수: <span th:text="${summary.totalPosts()}"></span></li>
    <li>전체 댓글 수: <span th:text="${summary.totalComments()}"></span></li>
</ul>
<canvas id="trendChart" width="800" height="300"></canvas>
<script th:inline="javascript">
    const labels = /*[[${trendLabels}]]*/ [];
    const userSignups = /*[[${userSignupCounts}]]*/ [];
    const courseCreations = /*[[${courseCreationCounts}]]*/ [];
    const postCreations = /*[[${postCreationCounts}]]*/ [];

    new Chart(document.getElementById('trendChart'), {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: '신규 가입자', data: userSignups, borderColor: '#2563eb' },
                { label: '신규 코스', data: courseCreations, borderColor: '#16a34a' },
                { label: '신규 게시글', data: postCreations, borderColor: '#d97706' }
            ]
        }
    });
</script>
</body>
</html>
```

- [ ] **Step 5: AdminDashboardController 구현**

`backend/src/main/java/com/runvas/backend/admin/AdminDashboardController.java`:

```java
package com.runvas.backend.admin;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardController {

    private final AdminStatsService adminStatsService;

    public AdminDashboardController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/admin/dashboard")
    String dashboard(Model model) {
        List<DailyTrendPoint> userSignupTrend = adminStatsService.userSignupTrend();
        List<DailyTrendPoint> courseCreationTrend = adminStatsService.courseCreationTrend();
        List<DailyTrendPoint> postCreationTrend = adminStatsService.postCreationTrend();

        model.addAttribute("summary", adminStatsService.summary());
        model.addAttribute("trendLabels", labelsOf(userSignupTrend));
        model.addAttribute("userSignupCounts", countsOf(userSignupTrend));
        model.addAttribute("courseCreationCounts", countsOf(courseCreationTrend));
        model.addAttribute("postCreationCounts", countsOf(postCreationTrend));
        return "admin/dashboard";
    }

    private List<String> labelsOf(List<DailyTrendPoint> trend) {
        return trend.stream().map(point -> point.day().toString()).toList();
    }

    private List<Long> countsOf(List<DailyTrendPoint> trend) {
        return trend.stream().map(DailyTrendPoint::count).toList();
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminDashboardControllerTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/templates/admin/fragments/nav.html \
        backend/src/main/resources/templates/admin/dashboard.html \
        backend/src/main/java/com/runvas/backend/admin/AdminDashboardController.java \
        backend/src/test/java/com/runvas/backend/admin/AdminDashboardControllerTest.java
git commit -m "feat(admin): 대시보드 통계/추이 화면 추가"
```

---

## Task 9: 회원 목록 화면

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminUserQueryService.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminUserController.java`
- Create: `backend/src/main/resources/templates/admin/users.html`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminUserControllerTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(String, String, Pageable)`(Task 6).
- Produces: `GET /admin/users?q=&page=` → 뷰 `admin/users`.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminUserControllerTest.java`:

```java
package com.runvas.backend.admin;

import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminUserControllerTest {

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

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void searchFiltersByNickname() throws Exception {
        userRepository.saveAndFlush(User.createKakaoUser("k1", "a@example.com", "서울러너", null));
        userRepository.saveAndFlush(User.createKakaoUser("k2", "b@example.com", "부산러너", null));

        mockMvc.perform(get("/admin/users").param("q", "서울"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("서울러너")))
                .andExpect(content().string(not(containsString("부산러너"))));
    }

    @Test
    void listRedirectsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminUserControllerTest"`
Expected: FAIL — 컨트롤러/뷰가 없어 404.

- [ ] **Step 3: AdminUserQueryService 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminUserQueryService.java`:

```java
package com.runvas.backend.admin;

import com.runvas.user.domain.User;
import com.runvas.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminUserQueryService {

    private final UserRepository userRepository;

    public AdminUserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<User> search(String q, int page, int size) {
        String keyword = q == null ? "" : q;
        return userRepository.findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                keyword, keyword, PageRequest.of(page, size));
    }
}
```

- [ ] **Step 4: 회원 목록 템플릿 작성**

`backend/src/main/resources/templates/admin/users.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>Runvas 관리자 - 회원</title>
</head>
<body>
<nav th:replace="~{admin/fragments/nav :: nav}"></nav>
<h1>회원 목록</h1>
<form method="get" th:action="@{/admin/users}">
    <input type="text" name="q" th:value="${q}" placeholder="닉네임 또는 이메일 검색"/>
    <button type="submit">검색</button>
</form>
<table>
    <thead>
    <tr>
        <th>ID</th>
        <th>닉네임</th>
        <th>이메일</th>
        <th>가입일</th>
        <th>탈퇴 여부</th>
    </tr>
    </thead>
    <tbody>
    <tr th:each="user : ${users}">
        <td th:text="${user.id}"></td>
        <td th:text="${user.nickname}"></td>
        <td th:text="${user.email}"></td>
        <td th:text="${user.createdAt}"></td>
        <td th:text="${user.deletedAt != null ? '탈퇴' : '-'}"></td>
    </tr>
    </tbody>
</table>
<div>
    <span th:text="'페이지 ' + (${page} + 1) + ' / ' + ${totalPages}"></span>
    <a th:if="${page > 0}" th:href="@{/admin/users(q=${q}, page=${page - 1})}">이전</a>
    <a th:if="${page + 1 < totalPages}" th:href="@{/admin/users(q=${q}, page=${page + 1})}">다음</a>
</div>
</body>
</html>
```

- [ ] **Step 5: AdminUserController 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminUserController.java`:

```java
package com.runvas.backend.admin;

import com.runvas.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminUserController {

    private static final int PAGE_SIZE = 20;

    private final AdminUserQueryService adminUserQueryService;

    public AdminUserController(AdminUserQueryService adminUserQueryService) {
        this.adminUserQueryService = adminUserQueryService;
    }

    @GetMapping("/admin/users")
    String users(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        Page<User> result = adminUserQueryService.search(q, page, PAGE_SIZE);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("users", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        return "admin/users";
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminUserControllerTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminUserQueryService.java \
        backend/src/main/java/com/runvas/backend/admin/AdminUserController.java \
        backend/src/main/resources/templates/admin/users.html \
        backend/src/test/java/com/runvas/backend/admin/AdminUserControllerTest.java
git commit -m "feat(admin): 회원 목록 화면 추가"
```

---

## Task 10: 코스 목록 화면

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminCourseQueryService.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminCourseController.java`
- Create: `backend/src/main/resources/templates/admin/courses.html`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminCourseControllerTest.java`

**Interfaces:**
- Consumes: `CourseRepository.findByTitleContainingIgnoreCase(String, Pageable)`,
  `CourseRepository.findByTitleContainingIgnoreCaseAndVisibility(String, CourseVisibility, Pageable)`(Task 6).
- Produces: `GET /admin/courses?q=&visibility=&page=` → 뷰 `admin/courses`.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminCourseControllerTest.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.common.GeoBounds;
import com.runvas.backend.common.GeoPoint;
import com.runvas.backend.common.RoutePoint;
import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminCourseControllerTest {

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
    CourseRepository courseRepository;

    private Course course(String title, CourseVisibility visibility) {
        RoutePoint point = new RoutePoint(37.5665, 126.978, 0);
        GeoBounds bounds = new GeoBounds(new GeoPoint(37.5665, 126.978), new GeoPoint(37.567, 126.979));
        return new Course(
                "author-1", title, null, List.of(point), List.of(point),
                1000, 600, bounds, visibility, Set.of());
    }

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void visibilityFilterOnlyShowsMatchingCourses() throws Exception {
        courseRepository.saveAndFlush(course("공개 코스", CourseVisibility.PUBLIC));
        courseRepository.saveAndFlush(course("비공개 코스", CourseVisibility.PRIVATE));

        mockMvc.perform(get("/admin/courses").param("visibility", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공개 코스")))
                .andExpect(content().string(not(containsString("비공개 코스"))));
    }

    @Test
    void listRedirectsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/courses"))
                .andExpect(status().is3xxRedirection());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminCourseControllerTest"`
Expected: FAIL — 컨트롤러/뷰가 없어 404.

- [ ] **Step 3: AdminCourseQueryService 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminCourseQueryService.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseRepository;
import com.runvas.backend.course.CourseVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminCourseQueryService {

    private final CourseRepository courseRepository;

    public AdminCourseQueryService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public Page<Course> search(String q, CourseVisibility visibility, int page, int size) {
        String keyword = q == null ? "" : q;
        PageRequest pageRequest = PageRequest.of(page, size);
        if (visibility == null) {
            return courseRepository.findByTitleContainingIgnoreCase(keyword, pageRequest);
        }
        return courseRepository.findByTitleContainingIgnoreCaseAndVisibility(keyword, visibility, pageRequest);
    }
}
```

- [ ] **Step 4: 코스 목록 템플릿 작성**

`backend/src/main/resources/templates/admin/courses.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>Runvas 관리자 - 코스</title>
</head>
<body>
<nav th:replace="~{admin/fragments/nav :: nav}"></nav>
<h1>코스 목록</h1>
<form method="get" th:action="@{/admin/courses}">
    <input type="text" name="q" th:value="${q}" placeholder="제목 검색"/>
    <select name="visibility">
        <option value="" th:selected="${visibility == null}">전체</option>
        <option value="PUBLIC" th:selected="${visibility != null and visibility.name() == 'PUBLIC'}">공개</option>
        <option value="PRIVATE" th:selected="${visibility != null and visibility.name() == 'PRIVATE'}">비공개</option>
    </select>
    <button type="submit">검색</button>
</form>
<table>
    <thead>
    <tr>
        <th>ID</th>
        <th>제목</th>
        <th>작성자 ID</th>
        <th>공개범위</th>
        <th>좋아요 수</th>
        <th>생성일</th>
    </tr>
    </thead>
    <tbody>
    <tr th:each="course : ${courses}">
        <td th:text="${course.id}"></td>
        <td th:text="${course.title}"></td>
        <td th:text="${course.authorId}"></td>
        <td th:text="${course.visibility}"></td>
        <td th:text="${course.likeCount}"></td>
        <td th:text="${course.createdAt}"></td>
    </tr>
    </tbody>
</table>
<div>
    <span th:text="'페이지 ' + (${page} + 1) + ' / ' + ${totalPages}"></span>
    <a th:if="${page > 0}" th:href="@{/admin/courses(q=${q}, visibility=${visibility}, page=${page - 1})}">이전</a>
    <a th:if="${page + 1 < totalPages}" th:href="@{/admin/courses(q=${q}, visibility=${visibility}, page=${page + 1})}">다음</a>
</div>
</body>
</html>
```

- [ ] **Step 5: AdminCourseController 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminCourseController.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.course.Course;
import com.runvas.backend.course.CourseVisibility;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminCourseController {

    private static final int PAGE_SIZE = 20;

    private final AdminCourseQueryService adminCourseQueryService;

    public AdminCourseController(AdminCourseQueryService adminCourseQueryService) {
        this.adminCourseQueryService = adminCourseQueryService;
    }

    @GetMapping("/admin/courses")
    String courses(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "visibility", required = false) CourseVisibility visibility,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        Page<Course> result = adminCourseQueryService.search(q, visibility, page, PAGE_SIZE);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("visibility", visibility);
        model.addAttribute("courses", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        return "admin/courses";
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminCourseControllerTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminCourseQueryService.java \
        backend/src/main/java/com/runvas/backend/admin/AdminCourseController.java \
        backend/src/main/resources/templates/admin/courses.html \
        backend/src/test/java/com/runvas/backend/admin/AdminCourseControllerTest.java
git commit -m "feat(admin): 코스 목록 화면 추가"
```

---

## Task 11: 게시글 목록 화면

**Files:**
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminPostQueryService.java`
- Create: `backend/src/main/java/com/runvas/backend/admin/AdminPostController.java`
- Create: `backend/src/main/resources/templates/admin/posts.html`
- Test: `backend/src/test/java/com/runvas/backend/admin/AdminPostControllerTest.java`

**Interfaces:**
- Consumes: `PostRepository.findByTitleContainingIgnoreCase(String, Pageable)`(Task 6).
- Produces: `GET /admin/posts?q=&page=` → 뷰 `admin/posts`.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/src/test/java/com/runvas/backend/admin/AdminPostControllerTest.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AdminPostControllerTest {

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
    PostRepository postRepository;

    @Test
    @WithMockUser(username = "operator", roles = "ADMIN")
    void searchFiltersByTitle() throws Exception {
        postRepository.saveAndFlush(new Post("author-1", "한강 러닝 후기", "본문", null, Set.of()));
        postRepository.saveAndFlush(new Post("author-1", "남산 등산 후기", "본문", null, Set.of()));

        mockMvc.perform(get("/admin/posts").param("q", "한강"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("한강 러닝 후기")))
                .andExpect(content().string(not(containsString("남산 등산 후기"))));
    }

    @Test
    void listRedirectsWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().is3xxRedirection());
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminPostControllerTest"`
Expected: FAIL — 컨트롤러/뷰가 없어 404.

- [ ] **Step 3: AdminPostQueryService 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminPostQueryService.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import com.runvas.backend.community.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminPostQueryService {

    private final PostRepository postRepository;

    public AdminPostQueryService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Page<Post> search(String q, int page, int size) {
        String keyword = q == null ? "" : q;
        return postRepository.findByTitleContainingIgnoreCase(keyword, PageRequest.of(page, size));
    }
}
```

- [ ] **Step 4: 게시글 목록 템플릿 작성**

`backend/src/main/resources/templates/admin/posts.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>Runvas 관리자 - 게시글</title>
</head>
<body>
<nav th:replace="~{admin/fragments/nav :: nav}"></nav>
<h1>게시글 목록</h1>
<form method="get" th:action="@{/admin/posts}">
    <input type="text" name="q" th:value="${q}" placeholder="제목 검색"/>
    <button type="submit">검색</button>
</form>
<table>
    <thead>
    <tr>
        <th>ID</th>
        <th>제목</th>
        <th>작성자 ID</th>
        <th>좋아요 수</th>
        <th>댓글 수</th>
        <th>생성일</th>
    </tr>
    </thead>
    <tbody>
    <tr th:each="post : ${posts}">
        <td th:text="${post.id}"></td>
        <td th:text="${post.title}"></td>
        <td th:text="${post.authorId}"></td>
        <td th:text="${post.likeCount}"></td>
        <td th:text="${post.commentCount}"></td>
        <td th:text="${post.createdAt}"></td>
    </tr>
    </tbody>
</table>
<div>
    <span th:text="'페이지 ' + (${page} + 1) + ' / ' + ${totalPages}"></span>
    <a th:if="${page > 0}" th:href="@{/admin/posts(q=${q}, page=${page - 1})}">이전</a>
    <a th:if="${page + 1 < totalPages}" th:href="@{/admin/posts(q=${q}, page=${page + 1})}">다음</a>
</div>
</body>
</html>
```

- [ ] **Step 5: AdminPostController 작성**

`backend/src/main/java/com/runvas/backend/admin/AdminPostController.java`:

```java
package com.runvas.backend.admin;

import com.runvas.backend.community.Post;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminPostController {

    private static final int PAGE_SIZE = 20;

    private final AdminPostQueryService adminPostQueryService;

    public AdminPostController(AdminPostQueryService adminPostQueryService) {
        this.adminPostQueryService = adminPostQueryService;
    }

    @GetMapping("/admin/posts")
    String posts(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
        Page<Post> result = adminPostQueryService.search(q, page, PAGE_SIZE);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("posts", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        return "admin/posts";
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `cd backend && ./gradlew test --tests "com.runvas.backend.admin.AdminPostControllerTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/runvas/backend/admin/AdminPostQueryService.java \
        backend/src/main/java/com/runvas/backend/admin/AdminPostController.java \
        backend/src/main/resources/templates/admin/posts.html \
        backend/src/test/java/com/runvas/backend/admin/AdminPostControllerTest.java
git commit -m "feat(admin): 게시글 목록 화면 추가"
```

---

## Task 12: 문서 반영

**Files:**
- Modify: `docs/product-scope.md`
- Create: `docs/admin-dashboard.md`
- Create: `backend/docs/implementations/admin-dashboard.md`

**Interfaces:**
- 없음 (문서 전용 태스크).

- [ ] **Step 1: product-scope.md에 운영자 도구 섹션 추가**

`docs/product-scope.md`에서 아래 구절을 찾는다.

```markdown
팔로우, DM, 푸시 알림, 추천 알고리즘, 기간별 랭킹은
커뮤니티 MVP 이후 확장으로 분리합니다.

## 디렉토리별 책임
```

아래로 교체한다.

```markdown
팔로우, DM, 푸시 알림, 추천 알고리즘, 기간별 랭킹은
커뮤니티 MVP 이후 확장으로 분리합니다.

## 운영자 도구

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인하기 위한 내부 전용 관리자 대시보드를
`backend/`에서 제공합니다. 세션 기반 운영자 로그인으로만 접근하며, 읽기 전용(통계 조회, 목록
조회)입니다. 위 핵심 사용자 흐름(1~8)이나 MVP 범위/제외 범위와는 무관한 내부 운영 도구이며,
상세 내용은 `docs/admin-dashboard.md`를 따릅니다.

## 디렉토리별 책임
```

이어서 아래 구절을 찾는다.

```markdown
### backend/

- 코스 저장 및 조회 API
- 좌표 데이터 검증
- 지도 범위 기반 검색
- GPX 응답 생성
- 카카오 소셜 로그인 및 사용자 인증
- 게시글, 댓글, 좋아요 API
- 작성자 권한 검증
- 페이지네이션과 인기순 정렬
- 데이터 영속화
```

아래로 교체한다.

```markdown
### backend/

- 코스 저장 및 조회 API
- 좌표 데이터 검증
- 지도 범위 기반 검색
- GPX 응답 생성
- 카카오 소셜 로그인 및 사용자 인증
- 게시글, 댓글, 좋아요 API
- 작성자 권한 검증
- 페이지네이션과 인기순 정렬
- 데이터 영속화
- 운영자 전용 관리자 대시보드 제공 (읽기 전용)
```

- [ ] **Step 2: docs/admin-dashboard.md 작성**

`docs/admin-dashboard.md`:

```markdown
# 운영자 관리자 대시보드

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인하기 위한 내부 전용 도구입니다.
`backend/`에서만 제공하며, 모바일 앱과 공유하는 API 계약이 아닙니다.

## 접근 방법

- 경로: `/admin/**` (Thymeleaf 서버사이드 렌더링)
- 인증: 세션 기반 폼 로그인 (`/admin/login`). Runvas 사용자 계정(카카오 로그인)과는 완전히
  별개인 `admin_accounts` 테이블 기반 계정을 사용합니다.
- 최초 계정 생성: UI가 없습니다. 앱 시작 시 `admin_accounts` 테이블이 비어 있고 환경변수
  `ADMIN_SEED_USERNAME`/`ADMIN_SEED_PASSWORD`가 설정되어 있으면 자동으로 계정 1건을 생성합니다.
  이후 추가 계정이 필요하면 운영자가 직접 DB에 넣습니다.

## 화면 목록

| 경로 | 설명 |
| --- | --- |
| `/admin/login` | 로그인 폼 |
| `/admin/dashboard` | 전체 회원/코스(공개·비공개)/게시글/댓글 수 + 최근 30일 일별 신규 가입자·코스·게시글 추이 라인 차트 |
| `/admin/users` | 회원 목록. 닉네임/이메일 부분 일치 검색, 페이지네이션(페이지당 20건), 탈퇴 여부 표시 |
| `/admin/courses` | 코스 목록. 제목 부분 일치 검색, 공개범위(`PUBLIC`/`PRIVATE`) 필터, 페이지네이션 |
| `/admin/posts` | 게시글 목록. 제목 부분 일치 검색, 페이지네이션 |

## 명시적 제외 범위

- 회원 정지, 코스/게시글/댓글 삭제 등 관리 쓰기 액션은 없습니다. 모든 화면은 조회 전용입니다.
- API 요청 수/에러율/응답시간 등 트래픽 계측 기능은 없습니다 (Actuator/Micrometer 미도입).
- 관리자 계정 셀프 서비스 생성/변경 UI는 없습니다.
- 모바일 앱과는 무관합니다.

## 관련 문서

- 설계 배경: `docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`
```

- [ ] **Step 3: backend/docs/implementations 기록 작성**

`backend/docs/implementations/admin-dashboard.md`:

```markdown
# 운영자 관리자 대시보드

## 배경

Runvas 운영자가 회원/코스/커뮤니티 사용 현황을 확인할 수 있는 내부 전용 도구가 없었다.
`docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`에서 합의한 대로 읽기 전용
관리자 대시보드를 `backend/`에 단독 구현했다 (모바일 변경 없음).

## 설계 결정

- 기존 `/api/**` JWT 인증 체인(`com.runvas.global.security.SecurityConfig`, `@Order(2)`)과
  별개로 `/admin/**` 전용 세션 폼 로그인 체인(`com.runvas.backend.admin.AdminSecurityConfig`,
  `@Order(1)`)을 추가했다.
- 운영자 계정은 Runvas 사용자 계정과 완전히 분리된 `admin_accounts` 테이블로 관리하고, 최초
  계정은 `ADMIN_SEED_USERNAME`/`ADMIN_SEED_PASSWORD` 환경변수로 앱 시작 시 자동 생성한다
  (`AdminAccountSeeder`). 관리자 계정 셀프 서비스 생성 UI는 없다.
- 통계 추이(최근 30일)는 `UserRepository`/`CourseRepository`/`PostRepository`에 추가한
  `countDailySince(Instant)` 쿼리(JPQL `cast(... as date)` + `group by`) 결과를
  `AdminStatsService`가 빈 날짜를 0으로 채워 30개짜리 배열로 만든다.
- 화면은 Thymeleaf 서버사이드 렌더링이고, 추이 차트는 별도 프론트엔드 빌드 없이 Chart.js를
  CDN으로 불러와 그린다.
- 관리 쓰기 액션(삭제/정지)과 API 트래픽 계측(Actuator/Micrometer)은 이번 범위에서 제외했다.

## 변경 파일

| 파일 | 변경 내용 |
|------|-----------|
| `db/migration/V14__create_admin_accounts.sql` | 신규 — `admin_accounts` 테이블 |
| `backend/admin/AdminAccount.java`, `AdminAccountRepository.java` | 신규 — 운영자 계정 엔티티/리포지토리 |
| `backend/admin/AdminAccountSeeder.java` | 신규 — 환경변수 기반 최초 계정 시드 |
| `backend/admin/AdminUserDetailsService.java`, `AdminSecurityConfig.java` | 신규 — `/admin/**` 세션 폼 로그인 |
| `global/security/SecurityConfig.java` | `@Order(2)` + `.securityMatcher("/api/**")` 추가 (동작 변경 없음, 매칭 범위만 명시) |
| `backend/admin/DailyCountProjection.java`, `AdminSummary.java`, `DailyTrendPoint.java`, `AdminStatsService.java` | 신규 — 통계 집계 |
| `user/repository/UserRepository.java`, `backend/course/CourseRepository.java`, `backend/community/PostRepository.java` | 관리자 조회용 검색/집계 쿼리 추가 |
| `backend/admin/AdminLoginController.java`, `AdminDashboardController.java`, `AdminUserController.java`, `AdminCourseController.java`, `AdminPostController.java` | 신규 — 화면 컨트롤러 |
| `backend/admin/AdminUserQueryService.java`, `AdminCourseQueryService.java`, `AdminPostQueryService.java` | 신규 — 목록 검색/페이지네이션 |
| `templates/admin/*.html` | 신규 — 로그인/대시보드/회원/코스/게시글 화면 |
| `application.yml` | `runvas.admin.seed-username`/`seed-password` 추가 |

## 참고

- 관련 스펙: `docs/superpowers/specs/2026-07-21-admin-dashboard-design.md`
```

- [ ] **Step 4: 전체 백엔드 테스트 실행 (회귀 확인)**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL` — 신규 admin 테스트 포함 전체 테스트 통과.

- [ ] **Step 5: Commit**

```bash
git add docs/product-scope.md docs/admin-dashboard.md backend/docs/implementations/admin-dashboard.md
git commit -m "docs: 운영자 관리자 대시보드 문서 반영"
```

---

## Task 13: 로컬 수동 검증

**Files:** 없음 (코드 변경 없이 로컬 실행으로 확인).

- [ ] **Step 1: 로컬 PostgreSQL/Redis 기동 확인**

Run: `docker ps` (backend가 필요로 하는 로컬 PostgreSQL/Redis 컨테이너가 떠 있는지 확인. 없으면
저장소의 기존 로컬 개발 안내(`backend/README.md`)를 따라 기동한다.)

- [ ] **Step 2: 환경변수와 함께 앱 실행**

Run:
```bash
cd backend && \
ADMIN_SEED_USERNAME=operator ADMIN_SEED_PASSWORD=local-test-password \
JWT_SECRET=dev-secret-dev-secret-dev-secret-dev-secret \
./gradlew bootRun
```
Expected: 앱이 기동되고 로그에 `Seeded initial admin account: operator`가 출력된다.

- [ ] **Step 3: 브라우저로 로그인 및 화면 확인**

1. `http://localhost:8921/admin/dashboard` 접속 → `/admin/login`으로 리다이렉트되는지 확인.
2. `operator` / `local-test-password`로 로그인 → `/admin/dashboard`로 이동하는지 확인.
3. 대시보드에 회원/코스/게시글/댓글 총계와 라인 차트가 렌더링되는지 확인.
4. 상단 네비게이션에서 회원/코스/게시글 목록으로 이동해 검색 입력, 코스 필터, 페이지 이동
   링크가 동작하는지 확인.
5. 로그아웃 버튼으로 로그아웃 후 다시 `/admin/dashboard` 접근 시 로그인 화면으로 돌아오는지 확인.

- [ ] **Step 4: 앱 종료**

Run: `Ctrl+C`로 `bootRun` 프로세스를 종료한다.

이 태스크는 코드 변경이 없으므로 커밋하지 않는다.

---

## Self-Review 결과

- **스펙 커버리지**: 설계 문서의 인증(Task 2~4), 화면 구성(Task 5, 8~11), 통계/추이(Task 6~7),
  문서 변경(Task 12), 제외 범위(Global Constraints에 명시)가 모두 태스크로 반영됨.
- **플레이스홀더 스캔**: "TBD"/"추후"/"적절히 처리" 등 표현 없음. 모든 스텝에 실행 가능한 전체
  코드/명령어 포함.
- **타입 일관성**: `DailyCountProjection`(Task 6에서 정의) → `AdminStatsService`(Task 7)가
  `List<DailyCountProjection>`을 소비, `DailyTrendPoint`(Task 7) → `AdminDashboardController`
  (Task 8)가 `List<DailyTrendPoint>`를 소비 — 이름/타입 일치 확인 완료. `AdminAccountRepository`/
  `AdminUserDetailsService`/`AdminSecurityConfig` 간 빈 이름과 생성자 시그니처 일치 확인 완료.
