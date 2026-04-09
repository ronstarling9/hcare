# Family Portal — Implementation Plan Part 1: Backend Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This is Part 1 of 3.** Complete all tasks here before starting Part 2 (`2026-04-08-add-family-portal-plan-2-backend-endpoints.md`).

**Goal:** Lay the backend foundation for the family portal: DB schema, domain entities, and extended security primitives.

**Architecture:** New `family_portal_tokens` table (SHA-256 hashed invite tokens); `Agency.timezone` field; extended `UserPrincipal` with nullable `clientId`; extended `JwtTokenProvider` with a portal-specific token generator.

**Tech Stack:** Java 25, Spring Boot 3.4.4, JJWT 0.12.6, Flyway, PostgreSQL (Testcontainers in tests), JUnit 5, Mockito.

---

## File Map

| Action | Path |
|--------|------|
| Create | `backend/src/main/resources/db/migration/V12__family_portal_tokens.sql` |
| Modify | `backend/src/main/resources/application.yml` |
| Modify | `backend/src/main/java/com/hcare/domain/Agency.java` |
| Modify | `backend/src/main/java/com/hcare/domain/FamilyPortalUserRepository.java` |
| Create | `backend/src/main/java/com/hcare/domain/FamilyPortalToken.java` |
| Create | `backend/src/main/java/com/hcare/domain/FamilyPortalTokenRepository.java` |
| Modify | `backend/src/main/java/com/hcare/security/JwtProperties.java` |
| Modify | `backend/src/main/java/com/hcare/security/JwtTokenProvider.java` |
| Modify | `backend/src/main/java/com/hcare/security/UserPrincipal.java` |
| Modify | `backend/src/main/java/com/hcare/security/JwtAuthenticationFilter.java` |
| Modify | `backend/src/main/java/com/hcare/config/SecurityConfig.java` |
| Create | `backend/src/test/java/com/hcare/security/JwtAuthenticationFilterTest.java` |
| Modify | `backend/src/test/java/com/hcare/AbstractIntegrationTest.java` |

---

## Task 1: Flyway V12 migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__family_portal_tokens.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V12: Family portal token infrastructure
--
-- 1. Add timezone to agencies (defaults to America/New_York — covers most US home care agencies).
--    Existing rows get the default. Admins can update via a future settings endpoint.
-- 2. Create family_portal_tokens for hashed one-time invite tokens (72-hour TTL).
--    tokenHash stores hex-encoded SHA-256 of the raw URL token — raw token is never persisted.
--    ON DELETE CASCADE ensures token rows are cleaned up when a FamilyPortalUser is removed.
-- 3. Fix FamilyPortalUser unique constraint from (agency_id, email) to (client_id, agency_id, email)
--    so the same family member can have portal access for two clients at the same agency
--    (e.g., adult child caring for two parents).

ALTER TABLE agencies
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) NOT NULL DEFAULT 'America/New_York';

ALTER TABLE family_portal_users
    DROP CONSTRAINT IF EXISTS uq_family_portal_users_agency_email,
    ADD CONSTRAINT uq_fpu_client_agency_email UNIQUE (client_id, agency_id, email);

CREATE TABLE family_portal_tokens (
    id          UUID         PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL,
    fpu_id      UUID         NOT NULL REFERENCES family_portal_users(id) ON DELETE CASCADE,
    client_id   UUID         NOT NULL,
    agency_id   UUID         NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_family_portal_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_fpt_fpu_id ON family_portal_tokens(fpu_id);
CREATE INDEX idx_fpt_expires_at ON family_portal_tokens(expires_at);
```

- [ ] **Step 2: Verify migration runs cleanly**

```bash
cd backend
mvn flyway:info
```
Expected: V12 shows as "Pending". Then:
```bash
mvn spring-boot:run &
sleep 10 && curl -s http://localhost:8080/actuator/health | grep UP || true
kill %1
```
If the app starts without a Flyway exception, the migration is valid. If Flyway fails, fix the SQL before continuing.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__family_portal_tokens.sql
git commit -m "feat: V12 migration — family_portal_tokens, agencies.timezone, fix fpu unique constraint"
```

---

## Task 2: application.yml — add portal config properties

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Add portal properties under the `hcare` section**

Add after the existing `hcare.jwt` block:

```yaml
  portal:
    base-url: ${APP_BASE_URL:http://localhost:5173}
    jwt:
      expiration-days: ${PORTAL_JWT_EXPIRATION_DAYS:30}
```

The full `hcare` section should now look like:
```yaml
hcare:
  jwt:
    secret: ${JWT_SECRET:dev-insecure-default-secret-not-for-production-use-must-be-at-least-256-bits}
    expiration-ms: 86400000
  portal:
    base-url: ${APP_BASE_URL:http://localhost:5173}
    jwt:
      expiration-days: ${PORTAL_JWT_EXPIRATION_DAYS:30}
  storage:
    provider: ${HCARE_STORAGE_PROVIDER:local}
    documents-dir: ${HCARE_DOCUMENTS_DIR:/var/hcare/documents}
    signing-key: ${HCARE_STORAGE_SIGNING_KEY:${JWT_SECRET:dev-insecure-default-secret-not-for-production-use-must-be-at-least-256-bits}}
    base-url: ${HCARE_BASE_URL:http://localhost:8080}
```

- [ ] **Step 2: Also add portal JWT secret to AbstractIntegrationTest** (tests use a hardcoded key; portal expiration needs to be set)

In `backend/src/test/java/com/hcare/AbstractIntegrationTest.java`, add to the `configureDataSource` method:
```java
registry.add("hcare.portal.jwt.expiration-days", () -> "30");
registry.add("hcare.portal.base-url", () -> "http://localhost:5173");
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application.yml \
        backend/src/test/java/com/hcare/AbstractIntegrationTest.java
git commit -m "feat: add portal.base-url and portal.jwt.expiration-days config properties"
```

---

## Task 3: Domain — Agency.timezone and FamilyPortalUserRepository

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/Agency.java`
- Modify: `backend/src/main/java/com/hcare/domain/FamilyPortalUserRepository.java`

- [ ] **Step 1: Add `timezone` field to `Agency`**

```java
// Add field after `state`:
@Column(nullable = false, length = 50)
private String timezone = "America/New_York";

// Add getter after getState():
public String getTimezone() { return timezone; }
public void setTimezone(String timezone) { this.timezone = timezone; }
```

The full `Agency.java` after changes:
```java
package com.hcare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "agencies")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "CHAR(2)")
    private String state;

    @Column(nullable = false, length = 50)
    private String timezone = "America/New_York";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected Agency() {}

    public Agency(String name, String state) {
        this.name = name;
        this.state = state;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getState() { return state; }
    public String getTimezone() { return timezone; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setState(String state) { this.state = state; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
```

- [ ] **Step 2: Add `findByClientIdAndAgencyIdAndEmail` to `FamilyPortalUserRepository`**

The `findOrCreate` logic in the invite service needs to look up by `(clientId, agencyId, email)`:

```java
package com.hcare.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyPortalUserRepository extends JpaRepository<FamilyPortalUser, UUID> {
    Optional<FamilyPortalUser> findByAgencyIdAndEmail(UUID agencyId, String email);
    Optional<FamilyPortalUser> findByClientIdAndAgencyIdAndEmail(UUID clientId, UUID agencyId, String email);
    List<FamilyPortalUser> findByClientId(UUID clientId);
    Page<FamilyPortalUser> findByClientId(UUID clientId, Pageable pageable);
}
```

- [ ] **Step 3: Compile to verify**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/Agency.java \
        backend/src/main/java/com/hcare/domain/FamilyPortalUserRepository.java
git commit -m "feat: add Agency.timezone field and FamilyPortalUserRepository.findByClientIdAndAgencyIdAndEmail"
```

---

## Task 4: Domain — FamilyPortalToken entity and repository

**Files:**
- Create: `backend/src/main/java/com/hcare/domain/FamilyPortalToken.java`
- Create: `backend/src/main/java/com/hcare/domain/FamilyPortalTokenRepository.java`

- [ ] **Step 1: Write the failing test (we'll use a domain IT to verify hash storage)**

Create `backend/src/test/java/com/hcare/domain/FamilyPortalTokenDomainIT.java`:

```java
package com.hcare.domain;

import com.hcare.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE family_portal_tokens, family_portal_users, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FamilyPortalTokenDomainIT extends AbstractIntegrationTest {

    @Autowired private AgencyRepository agencyRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private FamilyPortalUserRepository fpuRepo;
    @Autowired private FamilyPortalTokenRepository tokenRepo;

    private UUID clientId;
    private UUID agencyId;
    private UUID fpuId;

    @BeforeEach
    void seed() {
        Agency agency = agencyRepo.save(new Agency("Test Agency", "NY"));
        agencyId = agency.getId();
        clientId = clientRepo.save(new Client(agencyId, "Alice", "Test",
            java.time.LocalDate.of(1940, 1, 1))).getId();
        FamilyPortalUser fpu = fpuRepo.save(new FamilyPortalUser(clientId, agencyId, "family@example.com"));
        fpuId = fpu.getId();
    }

    @Test
    void savesTokenHash_notRawToken() {
        String fakeHash = "abc123def456";
        FamilyPortalToken token = new FamilyPortalToken(
            fakeHash, fpuId, clientId, agencyId,
            LocalDateTime.now(ZoneOffset.UTC).plusHours(72));
        FamilyPortalToken saved = tokenRepo.save(token);

        Optional<FamilyPortalToken> found = tokenRepo.findByTokenHash(fakeHash);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getFpuId()).isEqualTo(fpuId);
    }

    @Test
    void deleteExpired_removesOnlyExpiredRows() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        tokenRepo.save(new FamilyPortalToken("hash-expired", fpuId, clientId, agencyId,
            now.minusHours(1)));
        tokenRepo.save(new FamilyPortalToken("hash-valid", fpuId, clientId, agencyId,
            now.plusHours(71)));

        tokenRepo.deleteExpired(now);

        assertThat(tokenRepo.findByTokenHash("hash-expired")).isEmpty();
        assertThat(tokenRepo.findByTokenHash("hash-valid")).isPresent();
    }
}
```

- [ ] **Step 2: Run the test — expect it to fail (FamilyPortalToken doesn't exist yet)**

```bash
cd backend && mvn test -Dtest=FamilyPortalTokenDomainIT -q 2>&1 | tail -5
```
Expected: COMPILATION ERROR or test failure — `FamilyPortalToken` not found.

- [ ] **Step 3: Create `FamilyPortalToken.java`**

```java
package com.hcare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One-time invite token for the family portal.
 * tokenHash is hex-encoded SHA-256(rawToken). The raw token only exists in the invite URL
 * and is never stored. When a family member clicks the link, the backend recomputes the
 * hash and looks up this row — then deletes it (one-time use).
 */
@Entity
@Table(name = "family_portal_tokens")
public class FamilyPortalToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "fpu_id", nullable = false)
    private UUID fpuId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    protected FamilyPortalToken() {}

    public FamilyPortalToken(String tokenHash, UUID fpuId, UUID clientId,
                              UUID agencyId, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.fpuId = fpuId;
        this.clientId = clientId;
        this.agencyId = agencyId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public UUID getFpuId() { return fpuId; }
    public UUID getClientId() { return clientId; }
    public UUID getAgencyId() { return agencyId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Create `FamilyPortalTokenRepository.java`**

```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FamilyPortalTokenRepository extends JpaRepository<FamilyPortalToken, UUID> {

    Optional<FamilyPortalToken> findByTokenHash(String tokenHash);

    /**
     * Bulk-deletes expired rows. Called nightly by FamilyPortalTokenCleanupJob.
     * agencyFilter does NOT apply here (FamilyPortalToken has no @Filter) — explicit
     * JPQL delete is safe without filter injection.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM FamilyPortalToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
```

- [ ] **Step 5: Run the test — expect it to pass**

```bash
cd backend && mvn test -Dtest=FamilyPortalTokenDomainIT -q 2>&1 | tail -5
```
Expected: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/hcare/domain/FamilyPortalToken.java \
        backend/src/main/java/com/hcare/domain/FamilyPortalTokenRepository.java \
        backend/src/test/java/com/hcare/domain/FamilyPortalTokenDomainIT.java
git commit -m "feat: FamilyPortalToken entity and repository with nightly cleanup support"
```

---

## Task 5: Security — JwtProperties, JwtTokenProvider, UserPrincipal

**Files:**
- Modify: `backend/src/main/java/com/hcare/security/JwtProperties.java`
- Modify: `backend/src/main/java/com/hcare/security/JwtTokenProvider.java`
- Modify: `backend/src/main/java/com/hcare/security/UserPrincipal.java`

- [ ] **Step 1: Write the failing test for JwtTokenProvider portal token**

Create `backend/src/test/java/com/hcare/security/JwtTokenProviderTest.java`:

```java
package com.hcare.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private final String secret =
        "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algorithm-ok";

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(86_400_000L);
        props.setPortalExpirationDays(30);
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateFamilyPortalToken_claimsContainRoleAndClientId() {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        String token = provider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        Claims claims = provider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(fpuId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("FAMILY_PORTAL");
        assertThat(claims.get("clientId", String.class)).isEqualTo(clientId.toString());
        assertThat(claims.get("agencyId", String.class)).isEqualTo(agencyId.toString());
    }

    @Test
    void getClientId_extractsClientIdClaim() {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();

        String token = provider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        assertThat(provider.getClientId(token)).isEqualTo(clientId.toString());
    }

    @Test
    void adminToken_doesNotHaveClientIdClaim() {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String token = provider.generateToken(userId, agencyId, "ADMIN");
        Claims claims = provider.parseAndValidate(token);
        assertThat(claims.get("clientId", String.class)).isNull();
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd backend && mvn test -Dtest=JwtTokenProviderTest -q 2>&1 | tail -5
```
Expected: COMPILATION ERROR — `setPortalExpirationDays` not found.

- [ ] **Step 3: Update `JwtProperties` to add `portalExpirationDays`**

```java
package com.hcare.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hcare.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs;
    private int portalExpirationDays = 30;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getExpirationMs() { return expirationMs; }
    public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }

    public int getPortalExpirationDays() { return portalExpirationDays; }
    public void setPortalExpirationDays(int portalExpirationDays) {
        this.portalExpirationDays = portalExpirationDays;
    }
}
```

- [ ] **Step 4: Update `JwtTokenProvider` to add `generateFamilyPortalToken` and `getClientId`**

```java
package com.hcare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final int portalExpirationDays;

    public JwtTokenProvider(JwtProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(
            props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = props.getExpirationMs();
        this.portalExpirationDays = props.getPortalExpirationDays();
    }

    /** Generates an admin/scheduler JWT (existing method — unchanged). */
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

    /**
     * Generates a FAMILY_PORTAL JWT. Subject is fpUserId (the FamilyPortalUser.id).
     * Expiry is governed by portal.jwt.expiration-days (default 30), independent of
     * the admin jwt.expiration-ms.
     */
    public String generateFamilyPortalToken(UUID fpUserId, UUID clientId, UUID agencyId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(fpUserId.toString())
            .claim("agencyId", agencyId.toString())
            .claim("clientId", clientId.toString())
            .claim("role", "FAMILY_PORTAL")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(portalExpirationDays, ChronoUnit.DAYS)))
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

    /** Reads the clientId claim. Only present on FAMILY_PORTAL tokens. */
    public String getClientId(String token) {
        return parseClaims(token).get("clientId", String.class);
    }

    public Claims parseAndValidate(String token) {
        try {
            return parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
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

- [ ] **Step 5: Run test — expect pass**

```bash
cd backend && mvn test -Dtest=JwtTokenProviderTest -q 2>&1 | tail -5
```
Expected: Tests run: 3, Failures: 0.

- [ ] **Step 6: Update `UserPrincipal` to add nullable `clientId`**

```java
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
    /** Non-null only when role == FAMILY_PORTAL. Hard scope boundary for /family/portal/** endpoints. */
    private final UUID clientId;

    /** Constructor for admin/scheduler tokens (clientId is null). */
    public UserPrincipal(UUID userId, UUID agencyId, String role) {
        this(userId, agencyId, role, null);
    }

    /** Constructor for FAMILY_PORTAL tokens (clientId is non-null). */
    public UserPrincipal(UUID userId, UUID agencyId, String role, UUID clientId) {
        this.userId = userId;
        this.agencyId = agencyId;
        this.role = role;
        this.clientId = clientId;
    }

    public UUID getUserId() { return userId; }
    public UUID getAgencyId() { return agencyId; }
    public String getRole() { return role; }
    /** Returns the clientId scope for FAMILY_PORTAL tokens; null for admin/scheduler tokens. */
    public UUID getClientId() { return clientId; }

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

- [ ] **Step 7: Compile**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/hcare/security/JwtProperties.java \
        backend/src/main/java/com/hcare/security/JwtTokenProvider.java \
        backend/src/main/java/com/hcare/security/UserPrincipal.java \
        backend/src/test/java/com/hcare/security/JwtTokenProviderTest.java
git commit -m "feat: JwtTokenProvider.generateFamilyPortalToken + UserPrincipal.clientId scope field"
```

---

## Task 6: Security — JwtAuthenticationFilter and SecurityConfig

**Files:**
- Modify: `backend/src/main/java/com/hcare/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/hcare/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/hcare/security/JwtAuthenticationFilterTest.java`

- [ ] **Step 1: Write the failing test for JwtAuthenticationFilter**

```java
package com.hcare.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private JwtTokenProvider tokenProvider;
    private final String secret =
        "test-secret-key-must-be-at-least-256-bits-for-hmac-sha256-algorithm-ok";

    @Mock private FilterChain chain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(86_400_000L);
        props.setPortalExpirationDays(30);
        tokenProvider = new JwtTokenProvider(props);
        filter = new JwtAuthenticationFilter(tokenProvider);
        SecurityContextHolder.clearContext();
    }

    @Test
    void familyPortalToken_populatesClientIdOnPrincipal() throws Exception {
        UUID fpuId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String jwt = tokenProvider.generateFamilyPortalToken(fpuId, clientId, agencyId);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getRole()).isEqualTo("FAMILY_PORTAL");
        assertThat(principal.getClientId()).isEqualTo(clientId);
        assertThat(principal.getUserId()).isEqualTo(fpuId);
        assertThat(principal.getAgencyId()).isEqualTo(agencyId);
    }

    @Test
    void adminToken_leavesClientIdNull() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        String jwt = tokenProvider.generateToken(userId, agencyId, "ADMIN");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        UserPrincipal principal = (UserPrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getRole()).isEqualTo("ADMIN");
        assertThat(principal.getClientId()).isNull();
    }
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
cd backend && mvn test -Dtest=JwtAuthenticationFilterTest -q 2>&1 | tail -5
```
Expected: AssertionError — `principal.getClientId()` is null even for FAMILY_PORTAL (filter not updated yet).

- [ ] **Step 3: Update `JwtAuthenticationFilter` to extract `clientId` for FAMILY_PORTAL tokens**

```java
package com.hcare.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

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
        if (StringUtils.hasText(token)) {
            Claims claims = tokenProvider.parseAndValidate(token);
            if (claims != null) {
                UUID userId = UUID.fromString(claims.getSubject());
                UUID agencyId = UUID.fromString(claims.get("agencyId", String.class));
                String role = claims.get("role", String.class);

                // For FAMILY_PORTAL tokens, extract clientId and populate it on the principal.
                // This is the hard scope boundary — dashboard controller reads principal.getClientId()
                // to restrict data to exactly one client. Without this, clientId is silently null
                // and any auth check relying on it would pass or silently fail open.
                UUID clientId = null;
                if ("FAMILY_PORTAL".equals(role)) {
                    String clientIdStr = tokenProvider.getClientId(token);
                    if (clientIdStr != null) {
                        clientId = UUID.fromString(clientIdStr);
                    }
                }

                UserPrincipal principal = new UserPrincipal(userId, agencyId, role, clientId);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null,
                        principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
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

- [ ] **Step 4: Run test — expect pass**

```bash
cd backend && mvn test -Dtest=JwtAuthenticationFilterTest -q 2>&1 | tail -5
```
Expected: Tests run: 2, Failures: 0.

- [ ] **Step 5: Update `SecurityConfig` to permit the portal verify endpoint**

Add `POST /api/v1/family/auth/verify` to `permitAll`. The full `filterChain` method:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers("/api/v1/agencies/register").permitAll()
            .requestMatchers("/api/v1/family/auth/verify").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Bearer");
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"status\":401}");
            })
        )
        .build();
}
```

- [ ] **Step 6: Run all security tests**

```bash
cd backend && mvn test -Dtest="JwtTokenProviderTest,JwtAuthenticationFilterTest" -q 2>&1 | tail -5
```
Expected: Tests run: 5, Failures: 0.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/hcare/security/JwtAuthenticationFilter.java \
        backend/src/main/java/com/hcare/config/SecurityConfig.java \
        backend/src/test/java/com/hcare/security/JwtAuthenticationFilterTest.java
git commit -m "feat: JwtAuthenticationFilter extracts clientId for FAMILY_PORTAL; permit verify endpoint"
```

---

## Task 7: Verify full backend test suite passes

- [ ] **Step 1: Run all backend tests**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS, no test failures. Any failures here must be fixed before proceeding to Part 2.

- [ ] **Step 2: If tests pass, commit is complete — proceed to Part 2**

Continue with `docs/superpowers/plans/2026-04-08-add-family-portal-plan-2-backend-endpoints.md`.
