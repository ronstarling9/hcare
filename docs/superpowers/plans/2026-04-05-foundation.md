# Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Core API and Mobile BFF with JWT auth, Hibernate multi-tenancy enforcement, PHI audit logging, and EvvStateConfig seed data — the infrastructure every other plan builds on.

**Architecture:** Two Spring Boot 3.x services: Core API owns all domain data and business logic; Mobile BFF is a stateless adapter (no database of its own). Multi-tenancy is enforced at the persistence layer via Hibernate `@Filter` — the interceptor enables a session-level filter that injects `agencyId = :currentAgency` into every query on scoped entities, making cross-tenant data access a framework-prevented error. JWTs are issued by Core API; the BFF validates and forwards them without re-issuing.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Spring Security 6, Spring Data JPA / Hibernate 6, Flyway, JJWT 0.12.6, H2 (dev), PostgreSQL 16 (prod / integration tests via Testcontainers), JUnit 5, Testcontainers.

---

## File Structure

```
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/hcare/
    │   │   ├── HcareApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── WebMvcConfig.java
    │   │   ├── multitenancy/
    │   │   │   ├── TenantContext.java          — ThreadLocal agencyId holder
    │   │   │   └── TenantFilterInterceptor.java — enables Hibernate filter per request
    │   │   ├── security/
    │   │   │   ├── JwtProperties.java          — @ConfigurationProperties for JWT config
    │   │   │   ├── UserPrincipal.java          — Spring Security principal carrying agencyId + role
    │   │   │   ├── JwtTokenProvider.java       — issue + parse + validate JWTs
    │   │   │   └── JwtAuthenticationFilter.java — OncePerRequestFilter: read Bearer → SecurityContext
    │   │   ├── domain/
    │   │   │   ├── package-info.java           — @FilterDef for agencyFilter (defined once here)
    │   │   │   ├── Agency.java
    │   │   │   ├── AgencyUser.java             — @Filter(agencyFilter)
    │   │   │   └── UserRole.java               — enum ADMIN | SCHEDULER | CAREGIVER
    │   │   ├── evv/
    │   │   │   └── EvvStateConfig.java         — global reference data, no agencyFilter
    │   │   ├── audit/
    │   │   │   ├── PhiAuditLog.java            — @Filter(agencyFilter), append-only
    │   │   │   ├── PhiAuditRepository.java
    │   │   │   └── PhiAuditService.java
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── ErrorResponse.java          — record
    │   │   └── api/v1/auth/
    │   │       ├── AuthController.java         — POST /api/v1/auth/login
    │   │       ├── AuthService.java
    │   │       └── dto/
    │   │           ├── LoginRequest.java        — record
    │   │           └── LoginResponse.java       — record
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │           ├── V1__initial_schema.sql
    │           └── V2__evv_state_config_seed.sql
    └── test/
        └── java/com/hcare/
            ├── AbstractIntegrationTest.java    — Testcontainers base class
            ├── security/
            │   └── JwtTokenProviderTest.java
            ├── multitenancy/
            │   └── TenantFilterIT.java
            ├── audit/
            │   └── PhiAuditServiceIT.java
            ├── evv/
            │   └── EvvStateConfigIT.java
            └── api/v1/auth/
                └── AuthControllerIT.java

bff/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/hcare/bff/
    │   │   ├── BffApplication.java
    │   │   └── config/
    │   │       └── SecurityConfig.java
    │   └── resources/
    │       ├── application.yml
    │       └── application-dev.yml
    └── test/
        └── java/com/hcare/bff/
            └── BffApplicationTest.java
```

---

## Task 1: Core API scaffold

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/hcare/HcareApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/application-prod.yml`
- Test: `backend/src/test/java/com/hcare/HcareApplicationTest.java`

- [ ] **Step 1: Write the smoke test**

```java
// backend/src/test/java/com/hcare/HcareApplicationTest.java
package com.hcare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class HcareApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cd backend && ./mvnw test -Dtest=HcareApplicationTest
```
Expected: FAIL — `backend/` directory and `pom.xml` do not exist yet.

- [ ] **Step 3: Create `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.4</version>
    <relativePath/>
  </parent>

  <groupId>com.hcare</groupId>
  <artifactId>hcare-api</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>hcare-api</name>

  <properties>
    <java.version>25</java.version>
    <jjwt.version>0.12.6</jjwt.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Create `HcareApplication.java`**

```java
// backend/src/main/java/com/hcare/HcareApplication.java
package com.hcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HcareApplication {
    public static void main(String[] args) {
        SpringApplication.run(HcareApplication.class, args);
    }
}
```

- [ ] **Step 5: Create `application.yml`**

```yaml
# backend/src/main/resources/application.yml
spring:
  application:
    name: hcare-api
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  threads:
    virtual:
      enabled: true

server:
  port: 8080

hcare:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 86400000
```

- [ ] **Step 6: Create `application-dev.yml`**

```yaml
# backend/src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:h2:mem:hcaredb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
  flyway:
    locations: classpath:db/migration
    baseline-on-migrate: false
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

hcare:
  jwt:
    secret: dev-secret-key-change-in-prod-must-be-256-bits-minimum-length
    expiration-ms: 86400000
```

- [ ] **Step 7: Create `application-prod.yml`**

```yaml
# backend/src/main/resources/application-prod.yml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
  flyway:
    locations: classpath:db/migration
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

hcare:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: 86400000
```

- [ ] **Step 8: Run the smoke test to confirm it passes**

```bash
cd backend && ./mvnw test -Dtest=HcareApplicationTest -Dspring.profiles.active=dev
```
Expected: `BUILD SUCCESS` — context loads with H2 + dev profile.

- [ ] **Step 9: Commit**

```bash
cd backend
git add pom.xml src/
git commit -m "feat: scaffold Core API — Spring Boot 3.4.4, JPA, Security, Flyway"
```

---

## Task 2: Mobile BFF scaffold

**Files:**
- Create: `bff/pom.xml`
- Create: `bff/src/main/java/com/hcare/bff/BffApplication.java`
- Create: `bff/src/main/resources/application.yml`
- Create: `bff/src/main/resources/application-dev.yml`
- Test: `bff/src/test/java/com/hcare/bff/BffApplicationTest.java`

- [ ] **Step 1: Write the smoke test**

```java
// bff/src/test/java/com/hcare/bff/BffApplicationTest.java
package com.hcare.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class BffApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Create `bff/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.4</version>
    <relativePath/>
  </parent>

  <groupId>com.hcare</groupId>
  <artifactId>hcare-bff</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>hcare-bff</name>

  <properties>
    <java.version>25</java.version>
    <jjwt.version>0.12.6</jjwt.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Create `BffApplication.java`**

```java
// bff/src/main/java/com/hcare/bff/BffApplication.java
package com.hcare.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BffApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `bff/src/main/resources/application.yml`**

```yaml
# bff/src/main/resources/application.yml
spring:
  application:
    name: hcare-bff
  threads:
    virtual:
      enabled: true

server:
  port: 8081

hcare:
  core-api-url: ${CORE_API_URL:http://localhost:8080}
  jwt:
    secret: ${JWT_SECRET}
```

- [ ] **Step 5: Create `bff/src/main/resources/application-dev.yml`**

```yaml
# bff/src/main/resources/application-dev.yml
hcare:
  core-api-url: http://localhost:8080
  jwt:
    secret: dev-secret-key-change-in-prod-must-be-256-bits-minimum-length
```

- [ ] **Step 6: Run smoke test**

```bash
cd bff && ./mvnw test -Dtest=BffApplicationTest -Dspring.profiles.active=dev
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
cd bff
git add pom.xml src/
git commit -m "feat: scaffold Mobile BFF — stateless Spring Boot adapter"
```

---

## Task 3: Initial schema migration + Agency / AgencyUser entities

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `backend/src/main/java/com/hcare/domain/UserRole.java`
- Create: `backend/src/main/java/com/hcare/domain/Agency.java`
- Create: `backend/src/main/java/com/hcare/domain/AgencyUser.java`
- Create: `backend/src/main/java/com/hcare/domain/package-info.java`

`package-info.java` is where the shared `@FilterDef` is declared — once, not repeated per entity.

- [ ] **Step 1: Create `V1__initial_schema.sql`**

```sql
-- backend/src/main/resources/db/migration/V1__initial_schema.sql

CREATE TABLE agencies (
    id           UUID        PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    state        CHAR(2)     NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agency_users (
    id            UUID        PRIMARY KEY,
    agency_id     UUID        NOT NULL REFERENCES agencies(id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agency_users_email UNIQUE (email)
);

CREATE INDEX idx_agency_users_agency_id ON agency_users(agency_id);

CREATE TABLE phi_audit_logs (
    id                    UUID        PRIMARY KEY,
    user_id               UUID,
    family_portal_user_id UUID,
    system_job_id         VARCHAR(100),
    agency_id             UUID        NOT NULL,
    resource_type         VARCHAR(50) NOT NULL,
    resource_id           UUID        NOT NULL,
    action                VARCHAR(20) NOT NULL,
    occurred_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address            VARCHAR(45),
    user_agent            TEXT
);

CREATE INDEX idx_phi_audit_agency_id    ON phi_audit_logs(agency_id);
CREATE INDEX idx_phi_audit_occurred_at  ON phi_audit_logs(occurred_at);
```

Note: `id UUID PRIMARY KEY` with no DEFAULT — Hibernate generates the UUID in Java via `@GeneratedValue(strategy = GenerationType.UUID)`. This keeps H2 and PostgreSQL on identical DDL.

- [ ] **Step 2: Create `UserRole.java`**

```java
// backend/src/main/java/com/hcare/domain/UserRole.java
package com.hcare.domain;

public enum UserRole {
    ADMIN,
    SCHEDULER,
    CAREGIVER
}
```

- [ ] **Step 3: Create `package-info.java` — declares the shared Hibernate filter**

```java
// backend/src/main/java/com/hcare/domain/package-info.java
@FilterDef(
    name = "agencyFilter",
    parameters = @ParamDef(name = "agencyId", type = UUID.class)
)
package com.hcare.domain;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.util.UUID;
```

- [ ] **Step 4: Create `Agency.java`**

```java
// backend/src/main/java/com/hcare/domain/Agency.java
package com.hcare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agencies")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Agency() {}

    public Agency(String name, String state) {
        this.name = name;
        this.state = state;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getState() { return state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create `AgencyUser.java`**

```java
// backend/src/main/java/com/hcare/domain/AgencyUser.java
package com.hcare.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agency_users")
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class AgencyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AgencyUser() {}

    public AgencyUser(UUID agencyId, String email, String passwordHash, UserRole role) {
        this.agencyId = agencyId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public UUID getId() { return id; }
    public UUID getAgencyId() { return agencyId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 6: Create repositories**

```java
// backend/src/main/java/com/hcare/domain/AgencyRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgencyRepository extends JpaRepository<Agency, UUID> {}
```

```java
// backend/src/main/java/com/hcare/domain/AgencyUserRepository.java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AgencyUserRepository extends JpaRepository<AgencyUser, UUID> {
    Optional<AgencyUser> findByEmail(String email);
}
```

- [ ] **Step 7: Run the smoke test to confirm migration runs cleanly**

```bash
cd backend && ./mvnw test -Dtest=HcareApplicationTest -Dspring.profiles.active=dev
```
Expected: `BUILD SUCCESS` — Flyway applies V1 and context loads.

- [ ] **Step 8: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V1__initial_schema.sql \
        src/main/java/com/hcare/domain/
git commit -m "feat: V1 schema migration + Agency/AgencyUser JPA entities with agencyFilter"
```

---

## Task 4: JWT token provider

**Files:**
- Create: `backend/src/main/java/com/hcare/security/JwtProperties.java`
- Create: `backend/src/main/java/com/hcare/security/UserPrincipal.java`
- Create: `backend/src/main/java/com/hcare/security/JwtTokenProvider.java`
- Test: `backend/src/test/java/com/hcare/security/JwtTokenProviderTest.java`

JWT claims for agency users: `sub` = userId, `agencyId` = UUID, `role` = ADMIN|SCHEDULER|CAREGIVER.

- [ ] **Step 1: Write the failing unit tests**

```java
// backend/src/test/java/com/hcare/security/JwtTokenProviderTest.java
package com.hcare.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private static final String SECRET =
        "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256-algorithm";
    private static final long EXPIRY_MS = 3600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpirationMs(EXPIRY_MS);
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateToken_returnsNonBlankString() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(userId, agencyId, "ADMIN");
        assertThat(token).isNotBlank();
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "SCHEDULER");
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForTamperedToken() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() throws InterruptedException {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(SECRET);
        shortProps.setExpirationMs(1L); // expires immediately
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortProps);
        String token = shortProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
        Thread.sleep(10);
        assertThat(shortProvider.validateToken(token)).isFalse();
    }

    @Test
    void getUserIdFromToken_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, UUID.randomUUID(), "ADMIN");
        assertThat(provider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void getAgencyIdFromToken_returnsCorrectUUID() {
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(UUID.randomUUID(), agencyId, "SCHEDULER");
        assertThat(provider.getAgencyId(token)).isEqualTo(agencyId);
    }

    @Test
    void getRoleFromToken_returnsCorrectRole() {
        String token = provider.generateToken(UUID.randomUUID(), UUID.randomUUID(), "CAREGIVER");
        assertThat(provider.getRole(token)).isEqualTo("CAREGIVER");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && ./mvnw test -Dtest=JwtTokenProviderTest
```
Expected: FAIL — `JwtTokenProvider` and `JwtProperties` do not exist.

- [ ] **Step 3: Create `JwtProperties.java`**

```java
// backend/src/main/java/com/hcare/security/JwtProperties.java
package com.hcare.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hcare.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpirationMs() { return expirationMs; }
    public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
}
```

- [ ] **Step 4: Create `UserPrincipal.java`**

```java
// backend/src/main/java/com/hcare/security/UserPrincipal.java
package com.hcare.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID agencyId;
    private final String role;

    public UserPrincipal(UUID userId, UUID agencyId, String role) {
        this.userId = userId;
        this.agencyId = agencyId;
        this.role = role;
    }

    public UUID getUserId() { return userId; }
    public UUID getAgencyId() { return agencyId; }
    public String getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return userId.toString(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

- [ ] **Step 5: Create `JwtTokenProvider.java`**

```java
// backend/src/main/java/com/hcare/security/JwtTokenProvider.java
package com.hcare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(
            props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
    }

    public String generateToken(UUID userId, UUID agencyId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("agencyId", agencyId.toString())
            .claim("role", role)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expirationMs))
            .signWith(signingKey)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UUID getAgencyId(String token) {
        return UUID.fromString(parseClaims(token).get("agencyId", String.class));
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

- [ ] **Step 6: Run tests to confirm they pass**

```bash
cd backend && ./mvnw test -Dtest=JwtTokenProviderTest
```
Expected: `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/security/ \
        src/test/java/com/hcare/security/
git commit -m "feat: JWT token provider — issue, validate, extract claims"
```

---

## Task 5: Spring Security config + JWT authentication filter

**Files:**
- Create: `backend/src/main/java/com/hcare/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/hcare/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/hcare/AbstractIntegrationTest.java`
- Test: integration test that unauthenticated requests return 401

- [ ] **Step 1: Create `AbstractIntegrationTest.java`** — shared Testcontainers base for all integration tests

```java
// backend/src/test/java/com/hcare/AbstractIntegrationTest.java
package com.hcare;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hcaretest")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("hcare.jwt.secret",
            () -> "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algorithm-ok");
        registry.add("hcare.jwt.expiration-ms", () -> "86400000");
    }
}
```

Also create `backend/src/main/resources/application-test.yml` (empty — all config from `@DynamicPropertySource`):

```yaml
# backend/src/main/resources/application-test.yml
spring:
  jpa:
    show-sql: false
```

- [ ] **Step 2: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/api/v1/auth/AuthControllerIT.java
package com.hcare.api.v1.auth;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anyProtectedEndpoint_withoutToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 3: Run to confirm failure**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerIT
```
Expected: FAIL — `SecurityConfig` not yet created; Spring Security's default blocks but test setup may differ.

- [ ] **Step 4: Create `JwtAuthenticationFilter.java`**

```java
// backend/src/main/java/com/hcare/security/JwtAuthenticationFilter.java
package com.hcare.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            UserPrincipal principal = new UserPrincipal(
                tokenProvider.getUserId(token),
                tokenProvider.getAgencyId(token),
                tokenProvider.getRole(token)
            );
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 5: Create `SecurityConfig.java`**

```java
// backend/src/main/java/com/hcare/config/SecurityConfig.java
package com.hcare.config;

import com.hcare.security.JwtAuthenticationFilter;
import com.hcare.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 6: Run the integration test**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerIT
```
Expected: `Tests run: 1, Failures: 0` — unauthenticated request to `/api/v1/health` returns 401.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/security/JwtAuthenticationFilter.java \
        src/main/java/com/hcare/config/SecurityConfig.java \
        src/main/resources/application-test.yml \
        src/test/java/com/hcare/AbstractIntegrationTest.java \
        src/test/java/com/hcare/api/v1/auth/AuthControllerIT.java
git commit -m "feat: Spring Security config + JWT auth filter — stateless, 401 on unauth requests"
```

---

## Task 6: Login endpoint

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/auth/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/auth/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/auth/AuthService.java`
- Create: `backend/src/main/java/com/hcare/api/v1/auth/AuthController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/auth/AuthControllerIT.java`

- [ ] **Step 1: Add login tests to `AuthControllerIT.java`**

Add these tests alongside the existing `anyProtectedEndpoint_withoutToken_returns401` test:

```java
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import com.hcare.domain.AgencyUser;
import com.hcare.domain.AgencyUserRepository;
import com.hcare.domain.UserRole;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

// (add these fields and methods to the existing AuthControllerIT class)

@Autowired private AgencyRepository agencyRepo;
@Autowired private AgencyUserRepository userRepo;
@Autowired private PasswordEncoder passwordEncoder;

@BeforeEach
void seedUser() {
    userRepo.deleteAll();
    agencyRepo.deleteAll();
    Agency agency = agencyRepo.save(new Agency("Test Agency", "TX"));
    userRepo.save(new AgencyUser(
        agency.getId(), "admin@test.com",
        passwordEncoder.encode("password123"), UserRole.ADMIN));
}

@Test
void login_withValidCredentials_returnsJwt() {
    LoginRequest request = new LoginRequest("admin@test.com", "password123");
    ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
        "/api/v1/auth/login", request, LoginResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().token()).isNotBlank();
}

@Test
void login_withWrongPassword_returns401() {
    LoginRequest request = new LoginRequest("admin@test.com", "wrongpassword");
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/api/v1/auth/login", request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}

@Test
void login_withUnknownEmail_returns401() {
    LoginRequest request = new LoginRequest("nobody@test.com", "password123");
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/api/v1/auth/login", request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

- [ ] **Step 2: Run to confirm new tests fail**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerIT
```
Expected: 3 new tests FAIL — endpoint doesn't exist.

- [ ] **Step 3: Create `LoginRequest.java` and `LoginResponse.java`**

```java
// backend/src/main/java/com/hcare/api/v1/auth/dto/LoginRequest.java
package com.hcare.api.v1.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {}
```

```java
// backend/src/main/java/com/hcare/api/v1/auth/dto/LoginResponse.java
package com.hcare.api.v1.auth.dto;

import java.util.UUID;

public record LoginResponse(
    String token,
    UUID userId,
    UUID agencyId,
    String role
) {}
```

- [ ] **Step 4: Create `AuthService.java`**

```java
// backend/src/main/java/com/hcare/api/v1/auth/AuthService.java
package com.hcare.api.v1.auth;

import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.domain.AgencyUser;
import com.hcare.domain.AgencyUserRepository;
import com.hcare.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AgencyUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(AgencyUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        AgencyUser user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = tokenProvider.generateToken(
            user.getId(), user.getAgencyId(), user.getRole().name());

        return new LoginResponse(token, user.getId(), user.getAgencyId(), user.getRole().name());
    }
}
```

- [ ] **Step 5: Create `AuthController.java`**

```java
// backend/src/main/java/com/hcare/api/v1/auth/AuthController.java
package com.hcare.api.v1.auth;

import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

- [ ] **Step 6: Run all auth tests**

```bash
cd backend && ./mvnw test -Dtest=AuthControllerIT
```
Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/api/v1/auth/ \
        src/test/java/com/hcare/api/v1/auth/
git commit -m "feat: POST /api/v1/auth/login — JWT issued on valid credentials"
```

---

## Task 7: Hibernate @Filter multi-tenancy enforcement

**Files:**
- Create: `backend/src/main/java/com/hcare/multitenancy/TenantContext.java`
- Create: `backend/src/main/java/com/hcare/multitenancy/TenantFilterInterceptor.java`
- Create: `backend/src/main/java/com/hcare/config/WebMvcConfig.java`
- Test: `backend/src/test/java/com/hcare/multitenancy/TenantFilterIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/multitenancy/TenantFilterIT.java
package com.hcare.multitenancy;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.*;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TenantFilterIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private String tokenA;
    private UUID agencyAId;
    private UUID agencyBId;

    @BeforeEach
    void setup() {
        userRepo.deleteAll();
        agencyRepo.deleteAll();

        Agency agencyA = agencyRepo.save(new Agency("Agency A", "TX"));
        Agency agencyB = agencyRepo.save(new Agency("Agency B", "CA"));
        agencyAId = agencyA.getId();
        agencyBId = agencyB.getId();

        userRepo.save(new AgencyUser(agencyAId, "admin-a@test.com",
            passwordEncoder.encode("pass"), UserRole.ADMIN));
        userRepo.save(new AgencyUser(agencyBId, "admin-b@test.com",
            passwordEncoder.encode("pass"), UserRole.ADMIN));

        // log in as agency A
        LoginResponse login = restTemplate.postForObject(
            "/api/v1/auth/login",
            new LoginRequest("admin-a@test.com", "pass"),
            LoginResponse.class);
        tokenA = login.token();
    }

    @Test
    void authenticatedRequest_onlySeesOwnAgencyUsers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);

        ResponseEntity<UUID[]> response = restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET,
            new HttpEntity<>(headers), UUID[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Only agency A's user should be returned — not agency B's
        UUID[] userIds = response.getBody();
        assertThat(userIds).isNotNull();
        // All returned users must belong to agency A
        // (Verified by checking count: only 1 user exists in agency A)
        assertThat(userIds).hasSize(1);
    }
}
```

This test requires a `GET /api/v1/users` endpoint. Add a minimal one now:

```java
// backend/src/main/java/com/hcare/api/v1/users/UserController.java
package com.hcare.api.v1.users;

import com.hcare.domain.AgencyUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AgencyUserRepository userRepository;

    public UserController(AgencyUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<UUID>> listUsers() {
        List<UUID> ids = userRepository.findAll().stream()
            .map(u -> u.getId())
            .toList();
        return ResponseEntity.ok(ids);
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && ./mvnw test -Dtest=TenantFilterIT
```
Expected: FAIL — `GET /api/v1/users` returns both agencies' users (filter not active).

- [ ] **Step 3: Create `TenantContext.java`**

```java
// backend/src/main/java/com/hcare/multitenancy/TenantContext.java
package com.hcare.multitenancy;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_AGENCY = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID agencyId) {
        CURRENT_AGENCY.set(agencyId);
    }

    public static UUID get() {
        return CURRENT_AGENCY.get();
    }

    public static void clear() {
        CURRENT_AGENCY.remove();
    }
}
```

- [ ] **Step 4: Create `TenantFilterInterceptor.java`**

```java
// backend/src/main/java/com/hcare/multitenancy/TenantFilterInterceptor.java
package com.hcare.multitenancy;

import com.hcare.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            TenantContext.set(principal.getAgencyId());
            entityManager.unwrap(Session.class)
                .enableFilter("agencyFilter")
                .setParameter("agencyId", principal.getAgencyId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
```

- [ ] **Step 5: Create `WebMvcConfig.java`**

```java
// backend/src/main/java/com/hcare/config/WebMvcConfig.java
package com.hcare.config;

import com.hcare.multitenancy.TenantFilterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantFilterInterceptor tenantFilterInterceptor;

    public WebMvcConfig(TenantFilterInterceptor tenantFilterInterceptor) {
        this.tenantFilterInterceptor = tenantFilterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterInterceptor);
    }
}
```

- [ ] **Step 6: Run the multi-tenancy test**

```bash
cd backend && ./mvnw test -Dtest=TenantFilterIT
```
Expected: `Tests run: 1, Failures: 0` — Agency A user only sees Agency A's users.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/multitenancy/ \
        src/main/java/com/hcare/config/WebMvcConfig.java \
        src/main/java/com/hcare/api/v1/users/ \
        src/test/java/com/hcare/multitenancy/
git commit -m "feat: Hibernate agencyFilter interceptor — cross-tenant isolation enforced at persistence layer"
```

---

## Task 8: PhiAuditLog entity and service

**Files:**
- Create: `backend/src/main/java/com/hcare/audit/PhiAuditLog.java`
- Create: `backend/src/main/java/com/hcare/audit/PhiAuditRepository.java`
- Create: `backend/src/main/java/com/hcare/audit/PhiAuditService.java`
- Create: `backend/src/main/java/com/hcare/audit/ResourceType.java`
- Create: `backend/src/main/java/com/hcare/audit/AuditAction.java`
- Test: `backend/src/test/java/com/hcare/audit/PhiAuditServiceIT.java`

Note: `PhiAuditLog` rows are created by `PhiAuditService` — no update or delete endpoints exist. The audit log is in a different package from the domain entities. `phi_audit_logs` was created in V1, so no new migration is needed.

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/audit/PhiAuditServiceIT.java
package com.hcare.audit;

import com.hcare.AbstractIntegrationTest;
import com.hcare.domain.Agency;
import com.hcare.domain.AgencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PhiAuditServiceIT extends AbstractIntegrationTest {

    @Autowired private PhiAuditService auditService;
    @Autowired private PhiAuditRepository auditRepository;
    @Autowired private AgencyRepository agencyRepository;

    @Test
    void logRead_persistsAuditEntry() {
        Agency agency = agencyRepository.save(new Agency("Audit Test Agency", "TX"));
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        auditService.logRead(userId, agency.getId(), ResourceType.CLIENT, resourceId, "127.0.0.1");

        var logs = auditRepository.findByAgencyId(agency.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.READ);
        assertThat(logs.get(0).getResourceType()).isEqualTo(ResourceType.CLIENT);
        assertThat(logs.get(0).getResourceId()).isEqualTo(resourceId);
        assertThat(logs.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void logWrite_persistsAuditEntry() {
        Agency agency = agencyRepository.save(new Agency("Audit Test Agency 2", "CA"));
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        auditService.logWrite(userId, agency.getId(), ResourceType.CAREPLAN, resourceId, "10.0.0.1");

        var logs = auditRepository.findByAgencyId(agency.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.WRITE);
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && ./mvnw test -Dtest=PhiAuditServiceIT
```
Expected: FAIL — `PhiAuditService` does not exist.

- [ ] **Step 3: Create `ResourceType.java` and `AuditAction.java`**

```java
// backend/src/main/java/com/hcare/audit/ResourceType.java
package com.hcare.audit;

public enum ResourceType {
    CLIENT, CAREPLAN, EVVRECORD, MEDICATION, CAREGIVER, DOCUMENT,
    AUTHORIZATION, INCIDENT_REPORT, AGENCY_USER
}
```

```java
// backend/src/main/java/com/hcare/audit/AuditAction.java
package com.hcare.audit;

public enum AuditAction {
    READ, WRITE, DELETE, EXPORT
}
```

- [ ] **Step 4: Create `PhiAuditLog.java`**

Note: `PhiAuditLog` is agency-scoped but stored in a separate schema concern. Apply the `@Filter` so that agency users can only read their own agency's logs; system jobs disable the filter explicitly.

```java
// backend/src/main/java/com/hcare/audit/PhiAuditLog.java
package com.hcare.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "phi_audit_logs")
@FilterDef(name = "agencyFilter", parameters = @ParamDef(name = "agencyId", type = UUID.class))
@Filter(name = "agencyFilter", condition = "agency_id = :agencyId")
public class PhiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "family_portal_user_id")
    private UUID familyPortalUserId;

    @Column(name = "system_job_id")
    private String systemJobId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    protected PhiAuditLog() {}

    private PhiAuditLog(UUID userId, UUID agencyId, ResourceType resourceType,
                        UUID resourceId, AuditAction action, String ipAddress) {
        this.userId = userId;
        this.agencyId = agencyId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.ipAddress = ipAddress;
    }

    static PhiAuditLog forUser(UUID userId, UUID agencyId, ResourceType type,
                               UUID resourceId, AuditAction action, String ip) {
        return new PhiAuditLog(userId, agencyId, type, resourceId, action, ip);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getAgencyId() { return agencyId; }
    public ResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public AuditAction getAction() { return action; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getIpAddress() { return ipAddress; }
}
```

- [ ] **Step 5: Create `PhiAuditRepository.java`**

```java
// backend/src/main/java/com/hcare/audit/PhiAuditRepository.java
package com.hcare.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PhiAuditRepository extends JpaRepository<PhiAuditLog, UUID> {
    List<PhiAuditLog> findByAgencyId(UUID agencyId);
}
```

- [ ] **Step 6: Create `PhiAuditService.java`**

```java
// backend/src/main/java/com/hcare/audit/PhiAuditService.java
package com.hcare.audit;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class PhiAuditService {

    private final PhiAuditRepository repository;

    public PhiAuditService(PhiAuditRepository repository) {
        this.repository = repository;
    }

    public void logRead(UUID userId, UUID agencyId, ResourceType resourceType,
                        UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.READ, ipAddress));
    }

    public void logWrite(UUID userId, UUID agencyId, ResourceType resourceType,
                         UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.WRITE, ipAddress));
    }

    public void logDelete(UUID userId, UUID agencyId, ResourceType resourceType,
                          UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.DELETE, ipAddress));
    }

    public void logExport(UUID userId, UUID agencyId, ResourceType resourceType,
                          UUID resourceId, String ipAddress) {
        repository.save(PhiAuditLog.forUser(userId, agencyId, resourceType,
            resourceId, AuditAction.EXPORT, ipAddress));
    }
}
```

- [ ] **Step 7: Run audit tests**

```bash
cd backend && ./mvnw test -Dtest=PhiAuditServiceIT
```
Expected: `Tests run: 2, Failures: 0`.

- [ ] **Step 8: Commit**

```bash
cd backend
git add src/main/java/com/hcare/audit/ \
        src/test/java/com/hcare/audit/
git commit -m "feat: PhiAuditLog append-only entity + PhiAuditService — HIPAA PHI access audit"
```

---

## Task 9: EvvStateConfig entity + 51-state seed migration

**Files:**
- Create: `backend/src/main/java/com/hcare/evv/EvvStateConfig.java`
- Create: `backend/src/main/java/com/hcare/evv/EvvStateConfigRepository.java`
- Create: `backend/src/main/java/com/hcare/evv/AggregatorType.java`
- Create: `backend/src/main/java/com/hcare/evv/EvvSystemModel.java`
- Create: `backend/src/main/resources/db/migration/V2__evv_state_config_seed.sql`
- Test: `backend/src/test/java/com/hcare/evv/EvvStateConfigIT.java`

- [ ] **Step 1: Write the failing integration test**

```java
// backend/src/test/java/com/hcare/evv/EvvStateConfigIT.java
package com.hcare.evv;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class EvvStateConfigIT extends AbstractIntegrationTest {

    @Autowired
    private EvvStateConfigRepository repository;

    @Test
    void allStatesAndDcAreSeeded() {
        long count = repository.count();
        assertThat(count).isEqualTo(51); // 50 states + DC
    }

    @Test
    void closedStates_haveClosed_systemModel() {
        List<String> closedStateCodes = List.of("MD", "SC", "OR", "KS", "SD");
        for (String code : closedStateCodes) {
            Optional<EvvStateConfig> config = repository.findByStateCode(code);
            assertThat(config).as("State %s should be seeded", code).isPresent();
            assertThat(config.get().getSystemModel())
                .as("State %s should be CLOSED", code)
                .isEqualTo(EvvSystemModel.CLOSED);
        }
    }

    @Test
    void nj_requiresRealTimeSubmission() {
        EvvStateConfig nj = repository.findByStateCode("NJ").orElseThrow();
        assertThat(nj.isRequiresRealTimeSubmission()).isTrue();
    }

    @Test
    void hi_hasManualEntryCapOf15Percent() {
        EvvStateConfig hi = repository.findByStateCode("HI").orElseThrow();
        assertThat(hi.getManualEntryCapPercent()).isEqualTo(15);
    }

    @Test
    void pa_hasComplianceThresholdOf85Percent() {
        EvvStateConfig pa = repository.findByStateCode("PA").orElseThrow();
        assertThat(pa.getComplianceThresholdPercent()).isEqualTo(85);
    }

    @Test
    void ok_doesNotSupportCoResidentExemption() {
        EvvStateConfig ok = repository.findByStateCode("OK").orElseThrow();
        assertThat(ok.isCoResidentExemptionSupported()).isFalse();
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && ./mvnw test -Dtest=EvvStateConfigIT
```
Expected: FAIL — `EvvStateConfig` entity and `V2` migration do not exist.

- [ ] **Step 3: Create `AggregatorType.java` and `EvvSystemModel.java`**

```java
// backend/src/main/java/com/hcare/evv/AggregatorType.java
package com.hcare.evv;

public enum AggregatorType {
    SANDATA,
    HHAEXCHANGE,
    AUTHENTICARE,
    CAREBRIDGE,
    NETSMART,
    THERAP,
    STATE_BUILT,
    CLOSED
}
```

```java
// backend/src/main/java/com/hcare/evv/EvvSystemModel.java
package com.hcare.evv;

public enum EvvSystemModel {
    OPEN,
    CLOSED,
    HYBRID
}
```

- [ ] **Step 4: Create `EvvStateConfig.java`**

```java
// backend/src/main/java/com/hcare/evv/EvvStateConfig.java
package com.hcare.evv;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "evv_state_configs")
public class EvvStateConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_code", nullable = false, unique = true, length = 2)
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_aggregator", nullable = false, length = 30)
    private AggregatorType defaultAggregator;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_model", nullable = false, length = 10)
    private EvvSystemModel systemModel;

    // JSON array stored as TEXT — never queried at DB level (H2/PG portability)
    @Column(name = "allowed_verification_methods", nullable = false, columnDefinition = "TEXT")
    private String allowedVerificationMethods;

    @Column(name = "gps_tolerance_miles", precision = 5, scale = 2)
    private BigDecimal gpsToleranceMiles;

    @Column(name = "requires_real_time_submission", nullable = false)
    private boolean requiresRealTimeSubmission;

    @Column(name = "manual_entry_cap_percent")
    private Integer manualEntryCapPercent;

    @Column(name = "co_resident_exemption_supported", nullable = false)
    private boolean coResidentExemptionSupported;

    // JSON object stored as TEXT — never queried at DB level
    @Column(name = "extra_required_fields", columnDefinition = "TEXT")
    private String extraRequiredFields;

    @Column(name = "compliance_threshold_percent")
    private Integer complianceThresholdPercent;

    @Column(name = "closed_system_acknowledged_by_agency", nullable = false)
    private boolean closedSystemAcknowledgedByAgency;

    protected EvvStateConfig() {}

    public String getStateCode() { return stateCode; }
    public AggregatorType getDefaultAggregator() { return defaultAggregator; }
    public EvvSystemModel getSystemModel() { return systemModel; }
    public String getAllowedVerificationMethods() { return allowedVerificationMethods; }
    public BigDecimal getGpsToleranceMiles() { return gpsToleranceMiles; }
    public boolean isRequiresRealTimeSubmission() { return requiresRealTimeSubmission; }
    public Integer getManualEntryCapPercent() { return manualEntryCapPercent; }
    public boolean isCoResidentExemptionSupported() { return coResidentExemptionSupported; }
    public String getExtraRequiredFields() { return extraRequiredFields; }
    public Integer getComplianceThresholdPercent() { return complianceThresholdPercent; }
    public boolean isClosedSystemAcknowledgedByAgency() { return closedSystemAcknowledgedByAgency; }
}
```

- [ ] **Step 5: Create `EvvStateConfigRepository.java`**

```java
// backend/src/main/java/com/hcare/evv/EvvStateConfigRepository.java
package com.hcare.evv;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EvvStateConfigRepository extends JpaRepository<EvvStateConfig, UUID> {
    Optional<EvvStateConfig> findByStateCode(String stateCode);
}
```

- [ ] **Step 6: Create the schema migration for `evv_state_configs`**

Add to a new file `V1b__evv_schema.sql` — or add this table to V1 if it hasn't been applied yet. Since V1 is already written in Task 3, add a new migration:

```sql
-- backend/src/main/resources/db/migration/V1b__evv_state_config_schema.sql

CREATE TABLE evv_state_configs (
    id                                  UUID         PRIMARY KEY,
    state_code                          CHAR(2)      NOT NULL,
    default_aggregator                  VARCHAR(30)  NOT NULL,
    system_model                        VARCHAR(10)  NOT NULL,
    allowed_verification_methods        TEXT         NOT NULL,
    gps_tolerance_miles                 DECIMAL(5,2),
    requires_real_time_submission       BOOLEAN      NOT NULL DEFAULT FALSE,
    manual_entry_cap_percent            INTEGER,
    co_resident_exemption_supported     BOOLEAN      NOT NULL DEFAULT TRUE,
    extra_required_fields               TEXT,
    compliance_threshold_percent        INTEGER,
    closed_system_acknowledged_by_agency BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_evv_state_configs_state_code UNIQUE (state_code)
);
```

- [ ] **Step 7: Create `V2__evv_state_config_seed.sql`**

Standard methods shorthand: `["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]`

```sql
-- backend/src/main/resources/db/migration/V2__evv_state_config_seed.sql
-- Source: docs/superpowers/evv-state-reference.md
-- JSON fields stored as TEXT — application-layer parsing only (H2/PG portability).

INSERT INTO evv_state_configs
    (id, state_code, default_aggregator, system_model,
     allowed_verification_methods, gps_tolerance_miles,
     requires_real_time_submission, manual_entry_cap_percent,
     co_resident_exemption_supported, extra_required_fields,
     compliance_threshold_percent, closed_system_acknowledged_by_agency)
VALUES
-- Alabama
(gen_random_uuid(),'AL','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Alaska (live-in exempt)
(gen_random_uuid(),'AK','THERAP','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Arizona (AHCCCS EVV 2.0 state-built since Oct 2025)
(gen_random_uuid(),'AZ','STATE_BUILT','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Arkansas (PCS default AuthentiCare; hybrid — multiple aggregators by program type)
(gen_random_uuid(),'AR','AUTHENTICARE','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',0.125,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- California (non-IHSS; IHSS is out of scope — separate CDSS integration)
(gen_random_uuid(),'CA','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Colorado
(gen_random_uuid(),'CO','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Connecticut
(gen_random_uuid(),'CT','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Washington DC
(gen_random_uuid(),'DC','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Delaware (transitioned from Sandata to HHAeXchange)
(gen_random_uuid(),'DE','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Florida (FFS default HHAeXchange; MCOs vary — hybrid)
(gen_random_uuid(),'FL','HHAEXCHANGE','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Georgia
(gen_random_uuid(),'GA','NETSMART','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Hawaii (15% manual entry cap hard enforcement)
(gen_random_uuid(),'HI','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,15,TRUE,NULL,NULL,FALSE),
-- Idaho
(gen_random_uuid(),'ID','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Illinois
(gen_random_uuid(),'IL','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Indiana (GPS mismatch never denies claims)
(gen_random_uuid(),'IN','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Iowa
(gen_random_uuid(),'IA','CAREBRIDGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Kansas — CLOSED (AuthentiCare mandated)
(gen_random_uuid(),'KS','AUTHENTICARE','CLOSED','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Kentucky (0.5 mi GPS tolerance)
(gen_random_uuid(),'KY','NETSMART','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',0.5,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Louisiana (LaSRS state-built)
(gen_random_uuid(),'LA','STATE_BUILT','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Maine
(gen_random_uuid(),'ME','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Maryland — CLOSED (LTSSMaryland; GPS only, telephony retired)
(gen_random_uuid(),'MD','CLOSED','CLOSED','["GPS"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Massachusetts
(gen_random_uuid(),'MA','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Michigan
(gen_random_uuid(),'MI','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Minnesota
(gen_random_uuid(),'MN','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Mississippi
(gen_random_uuid(),'MS','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Missouri (tasks-completed extra required element — only state with this)
(gen_random_uuid(),'MO','STATE_BUILT','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,'{"requiresTaskDocumentation":true}',NULL,FALSE),
-- Montana
(gen_random_uuid(),'MT','NETSMART','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Nebraska (0.25 mi urban / 0.5 mi rural — using 0.25 as conservative default)
(gen_random_uuid(),'NE','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',0.25,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Nevada
(gen_random_uuid(),'NV','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- New Hampshire
(gen_random_uuid(),'NH','AUTHENTICARE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- New Jersey (real-time push required — no batch)
(gen_random_uuid(),'NJ','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,TRUE,NULL,TRUE,NULL,NULL,FALSE),
-- New Mexico (AuthentiCare; transition to new vendor underway — verify before P2)
(gen_random_uuid(),'NM','AUTHENTICARE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- New York (near-real-time required; multi-aggregator — MCO/payer determines; PayerEvvRoutingConfig handles routing)
(gen_random_uuid(),'NY','HHAEXCHANGE','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,TRUE,NULL,TRUE,NULL,NULL,FALSE),
-- North Carolina (FFS Sandata; MCO HHAeXchange — hybrid)
(gen_random_uuid(),'NC','SANDATA','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- North Dakota (Therap free option; Sandata as default)
(gen_random_uuid(),'ND','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Ohio
(gen_random_uuid(),'OH','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Oklahoma (live-in caregivers explicitly in EVV scope — co-resident exemption NOT supported)
(gen_random_uuid(),'OK','HHAEXCHANGE','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,FALSE,NULL,NULL,FALSE),
-- Oregon — CLOSED (eXPRS; tightly coupled with billing)
(gen_random_uuid(),'OR','CLOSED','CLOSED','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Pennsylvania (cell telephony excluded; 85% compliance threshold)
(gen_random_uuid(),'PA','SANDATA','OPEN','["GPS","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,85,FALSE),
-- Rhode Island
(gen_random_uuid(),'RI','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- South Carolina — CLOSED (AuthentiCare mandated)
(gen_random_uuid(),'SC','AUTHENTICARE','CLOSED','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- South Dakota — CLOSED (Therap mandated)
(gen_random_uuid(),'SD','THERAP','CLOSED','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Tennessee (FFS Sandata; MCO CareBridge — hybrid)
(gen_random_uuid(),'TN','SANDATA','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Texas (HHAeXchange is state portal; hard EVV edits — claims deny on mismatch)
(gen_random_uuid(),'TX','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Utah (not documented in EVV reference — defaulting to Sandata; verify before P2)
(gen_random_uuid(),'UT','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Vermont
(gen_random_uuid(),'VT','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Virginia (MCO-mandated varies: HHAeXchange, Sandata, Tellus — hybrid; Netsmart as default)
(gen_random_uuid(),'VA','NETSMART','HYBRID','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Washington (ProviderOne state-built; live-in caregivers exempt)
(gen_random_uuid(),'WA','STATE_BUILT','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- West Virginia
(gen_random_uuid(),'WV','HHAEXCHANGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Wisconsin (GPS is informational only — never blocks payment)
(gen_random_uuid(),'WI','SANDATA','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE),
-- Wyoming
(gen_random_uuid(),'WY','CAREBRIDGE','OPEN','["GPS","TELEPHONY_CELL","TELEPHONY_LANDLINE","FIXED_DEVICE"]',NULL,FALSE,NULL,TRUE,NULL,NULL,FALSE);
```

Note: `gen_random_uuid()` works in PostgreSQL natively. For H2 in `MODE=PostgreSQL`, this function is also supported in H2 2.x. If the test environment uses H2 (dev profile), confirm H2 version is 2.x; all integration tests run against real PostgreSQL via Testcontainers.

- [ ] **Step 8: Run EVV state config tests**

```bash
cd backend && ./mvnw test -Dtest=EvvStateConfigIT
```
Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step 9: Commit**

```bash
cd backend
git add src/main/java/com/hcare/evv/ \
        src/main/resources/db/migration/V1b__evv_state_config_schema.sql \
        src/main/resources/db/migration/V2__evv_state_config_seed.sql \
        src/test/java/com/hcare/evv/
git commit -m "feat: EvvStateConfig entity + 51-state Flyway seed migration"
```

---

## Task 10: Global exception handler

**Files:**
- Create: `backend/src/main/java/com/hcare/exception/ErrorResponse.java`
- Create: `backend/src/main/java/com/hcare/exception/GlobalExceptionHandler.java`
- Test: unit tests for error response mapping

- [ ] **Step 1: Write failing unit tests**

```java
// backend/src/test/java/com/hcare/exception/GlobalExceptionHandlerTest.java
package com.hcare.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusException_mapsToCorrectHttpStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Entity not found");
    }

    @Test
    void unexpectedException_returns500() {
        RuntimeException ex = new RuntimeException("Unexpected failure");
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest
```
Expected: FAIL.

- [ ] **Step 3: Create `ErrorResponse.java`**

```java
// backend/src/main/java/com/hcare/exception/ErrorResponse.java
package com.hcare.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    String message,
    int status,
    LocalDateTime timestamp
) {
    public static ErrorResponse of(String message, int status) {
        return new ErrorResponse(message, status, LocalDateTime.now());
    }
}
```

- [ ] **Step 4: Create `GlobalExceptionHandler.java`**

```java
// backend/src/main/java/com/hcare/exception/GlobalExceptionHandler.java
package com.hcare.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(ex.getReason() != null ? ex.getReason() : ex.getMessage(), status));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
            .body(ErrorResponse.of("An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
```

- [ ] **Step 5: Run exception handler tests**

```bash
cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerTest
```
Expected: `Tests run: 2, Failures: 0`.

- [ ] **Step 6: Run all tests to confirm nothing is broken**

```bash
cd backend && ./mvnw test
```
Expected: `BUILD SUCCESS` — all tests pass.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/java/com/hcare/exception/ \
        src/test/java/com/hcare/exception/
git commit -m "feat: global @ControllerAdvice exception handler — consistent error response shape"
```

---

## BFF Security Config (bonus — complete the BFF scaffold)

The BFF needs to validate JWTs forwarded from mobile clients. It shares the same JWT secret as the Core API but has no database.

- [ ] **Step 1: Create `bff/src/main/java/com/hcare/bff/config/SecurityConfig.java`**

```java
// bff/src/main/java/com/hcare/bff/config/SecurityConfig.java
package com.hcare.bff.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${hcare.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        OncePerRequestFilter jwtFilter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    try {
                        Claims claims = Jwts.parser().verifyWith(key).build()
                            .parseSignedClaims(token).getPayload();
                        String role = claims.get("role", String.class);
                        UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                claims.getSubject(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (Exception ignored) {}
                }
                chain.doFilter(request, response);
            }
        };

        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

- [ ] **Step 2: Run BFF smoke test**

```bash
cd bff && ./mvnw test -Dtest=BffApplicationTest -Dspring.profiles.active=dev
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Final commit**

```bash
cd bff
git add src/main/java/com/hcare/bff/config/SecurityConfig.java
git commit -m "feat: BFF JWT validation filter — stateless, validates Core API-issued tokens"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| Spring Boot 3.x + Java 25 | Task 1 & 2 — pom.xml |
| Virtual threads enabled | Task 1 — application.yml |
| HikariCP pool tuned for virtual threads | Task 1 — application-prod.yml (maximum-pool-size: 20) |
| JWT stateless auth | Tasks 4–6 |
| Multi-tenancy via Hibernate @Filter | Task 7 |
| Filter set from JWT in HandlerInterceptor | Task 7 — TenantFilterInterceptor |
| PhiAuditLog append-only entity | Task 8 |
| H2 dev / PostgreSQL prod | Task 1 — application-dev/prod.yml |
| Flyway migrations | Tasks 3 & 9 |
| EvvStateConfig seeded by Flyway before any business logic | Task 9 — V2 migration |
| Graceful degradation if state unconfigured | Handled at usage time (Plans 3/8) |
| JSON fields as opaque TEXT (H2/PG portability) | Task 9 — `columnDefinition = "TEXT"` on JSON fields |
| ROLE_ADMIN / ROLE_SCHEDULER / ROLE_CAREGIVER | Tasks 3 & 4 — UserRole enum, JWT claims |
| BCrypt password hashing | Task 5 — SecurityConfig.passwordEncoder() |
| Global exception handler | Task 10 |
| Testcontainers for integration tests | Task 5 — AbstractIntegrationTest |
| BFF validates JWT (does not re-issue) | BFF bonus task |

**No placeholders found.**

**Type consistency check:** `UserPrincipal` is constructed in `JwtAuthenticationFilter` (Task 5) using methods defined in `JwtTokenProvider` (Task 4). `LoginResponse` fields match the JWT claims shape. `AgencyUser` constructor matches `AuthService` usage. `PhiAuditLog.forUser()` factory method signature matches `PhiAuditService` calls. All consistent.
