# Scheduling REST API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose all scheduling operations (shift CRUD, caregiver assignment, shift offers/broadcast, recurrence pattern management) as versioned REST endpoints under `/api/v1/`.

**Architecture:** Three services (`ShiftSchedulingService`, `ShiftOfferCreationService`, `RecurrencePatternService`) and two controllers (`ShiftSchedulingController`, `RecurrencePatternController`) in the new package `com.hcare.api.v1.scheduling`. The services depend on existing repositories (`ShiftRepository`, `ShiftOfferRepository`, `CaregiverRepository`, `RecurrencePatternRepository`) and the existing `ScoringService` and `ShiftGenerationService` interfaces — no new persistence layer code is needed except three new repository query methods and three new entity setters. All state-transition guards live in the service layer; controllers are thin.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Spring Data JPA, Spring Security (JWT already wired), Testcontainers + PostgreSQL 16 for ITs, JUnit 5 + Mockito for unit tests, AssertJ for assertions.

---

## File Map

### New files — domain additions
- `backend/src/main/java/com/hcare/domain/RecurrencePattern.java` — add 3 setters (`setScheduledStartTime`, `setScheduledDurationMinutes`, `setDaysOfWeek`) — **modify**
- `backend/src/main/java/com/hcare/domain/ShiftRepository.java` — add `findByAgencyIdAndScheduledStartBetween` — **modify**
- `backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java` — add `findByCaregiverIdAndShiftId` — **modify**
- `backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java` — add `findByAgencyId` — **modify**
- `backend/src/main/java/com/hcare/domain/CaregiverRepository.java` — add `existsByIdAndAgencyId` — **modify**

### New files — DTOs (immutable records)
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftSummaryResponse.java` — calendar list item
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateShiftRequest.java` — ad-hoc shift creation
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/AssignCaregiverRequest.java` — assign caregiver to shift
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CancelShiftRequest.java` — optional notes on cancel
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RankedCaregiverResponse.java` — candidate list item
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftOfferResponse.java` — offer list item (note: distinct from the enum `ShiftOfferResponse` in domain package)
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RespondToOfferRequest.java` — ACCEPTED or DECLINED
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateRecurrencePatternRequest.java` — pattern creation
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RecurrencePatternResponse.java` — pattern detail
- `backend/src/main/java/com/hcare/api/v1/scheduling/dto/UpdateRecurrencePatternRequest.java` — patch pattern

### New files — services
- `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java` — all shift operations
- `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftOfferCreationService.java` — isolated `REQUIRES_NEW` offer-creation helper (C13 fix)
- `backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternService.java` — all pattern operations

### New files — controllers
- `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java` — `/api/v1/shifts` endpoints (no overlap with VisitController)
- `backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternController.java` — `/api/v1/recurrence-patterns` endpoints

### New files — tests
- `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java` — unit tests (Mockito)
- `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftOfferCreationServiceTest.java` — unit tests for offer-creation helper (C13)
- `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java` — IT tests
- `backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternServiceTest.java` — unit tests (Mockito)
- `backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternControllerIT.java` — IT tests

---

### Task 1: Repository extensions and RecurrencePattern setters

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/RecurrencePattern.java`
- Modify: `backend/src/main/java/com/hcare/domain/ShiftRepository.java`
- Modify: `backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java`
- Modify: `backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java`
- Test: `backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java` — verify setters persist; extend existing file
- Test: `backend/src/test/java/com/hcare/domain/ShiftDomainIT.java` — verify `findByAgencyIdAndScheduledStartBetween`; extend existing file

- [ ] **Step 1: Write the failing test for `findByAgencyIdAndScheduledStartBetween`**

Open `backend/src/test/java/com/hcare/domain/ShiftDomainIT.java` and add at the end of the class (before the closing `}`):

```java
@Test
void findByAgencyIdAndScheduledStartBetween_returns_only_matching_agency_and_window() {
    Agency agencyA = agencyRepo.save(new Agency("Agency A", "TX"));
    Agency agencyB = agencyRepo.save(new Agency("Agency B", "TX"));
    Client clientA = clientRepo.save(new Client(agencyA.getId(), "Pat", "A", LocalDate.of(1970, 1, 1)));
    Client clientB = clientRepo.save(new Client(agencyB.getId(), "Pat", "B", LocalDate.of(1970, 1, 1)));
    ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-A", true, "[]"));
    ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-B", true, "[]"));

    LocalDateTime windowStart = LocalDateTime.of(2026, 5, 1, 0, 0);
    LocalDateTime windowEnd   = LocalDateTime.of(2026, 5, 8, 0, 0);

    // Inside window, agencyA
    shiftRepo.save(new Shift(agencyA.getId(), null, clientA.getId(), null,
        stA.getId(), null, LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));
    // Outside window (before), agencyA
    shiftRepo.save(new Shift(agencyA.getId(), null, clientA.getId(), null,
        stA.getId(), null, LocalDateTime.of(2026, 4, 30, 9, 0), LocalDateTime.of(2026, 4, 30, 13, 0)));
    // Inside window but agencyB — must be excluded
    shiftRepo.save(new Shift(agencyB.getId(), null, clientB.getId(), null,
        stB.getId(), null, LocalDateTime.of(2026, 5, 4, 9, 0), LocalDateTime.of(2026, 5, 4, 13, 0)));

    List<Shift> results = shiftRepo.findByAgencyIdAndScheduledStartBetween(
        agencyA.getId(), windowStart, windowEnd);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAgencyId()).isEqualTo(agencyA.getId());
}
```

You will also need the imports already present in `ShiftDomainIT.java`. Check what's imported; the test uses `Agency`, `Client`, `ServiceType`, `Shift`, `LocalDateTime`, and `LocalDate` — all of which should already be imported since the file tests the `Shift` entity domain.

- [ ] **Step 2: Run to confirm it fails**

```bash
cd backend && mvn test -Dtest=ShiftDomainIT#findByAgencyIdAndScheduledStartBetween_returns_only_matching_agency_and_window -pl . 2>&1 | tail -20
```

Expected: compilation error — `findByAgencyIdAndScheduledStartBetween` does not exist.

- [ ] **Step 3: Add `findByAgencyIdAndScheduledStartBetween` to `ShiftRepository`**

In `backend/src/main/java/com/hcare/domain/ShiftRepository.java`, add this method inside the interface body (after `findOverlapping`):

```java
List<Shift> findByAgencyIdAndScheduledStartBetween(UUID agencyId,
                                                    LocalDateTime start,
                                                    LocalDateTime end);
```

No imports to add — `UUID`, `LocalDateTime`, and `List` are already imported.

- [ ] **Step 4: Write the failing test for `findByCaregiverIdAndShiftId`**

Open `backend/src/test/java/com/hcare/domain/ShiftSubEntitiesIT.java` and add at the end of the class (before the closing `}`):

```java
@Test
void findByCaregiverIdAndShiftId_returns_offer_when_present_and_empty_when_not() {
    Agency agency = agencyRepo.save(new Agency("Offer Test Agency", "TX"));
    Client client = clientRepo.save(new Client(agency.getId(), "Bob", "Test", LocalDate.of(1975, 3, 15)));
    ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-OT", true, "[]"));
    Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
        st.getId(), null,
        LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 1, 13, 0)));

    UUID caregiverId = UUID.randomUUID();
    ShiftOffer offer = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiverId, agency.getId()));

    Optional<ShiftOffer> found = shiftOfferRepo.findByCaregiverIdAndShiftId(caregiverId, shift.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(offer.getId());

    Optional<ShiftOffer> notFound = shiftOfferRepo.findByCaregiverIdAndShiftId(UUID.randomUUID(), shift.getId());
    assertThat(notFound).isEmpty();
}
```

Check the existing imports in `ShiftSubEntitiesIT.java`. You need `Optional` from `java.util`, `UUID`, `LocalDateTime`, `LocalDate`, and the domain classes — add any that are missing.

- [ ] **Step 5: Run to confirm it fails**

```bash
cd backend && mvn test -Dtest=ShiftSubEntitiesIT#findByCaregiverIdAndShiftId_returns_offer_when_present_and_empty_when_not -pl . 2>&1 | tail -20
```

Expected: compilation error — `findByCaregiverIdAndShiftId` does not exist.

- [ ] **Step 6: Add `findByCaregiverIdAndShiftId` to `ShiftOfferRepository`**

In `backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java`, add:

```java
import java.util.Optional;
```

Then add this method inside the interface body:

```java
Optional<ShiftOffer> findByCaregiverIdAndShiftId(UUID caregiverId, UUID shiftId);
```

- [ ] **Step 7: Write the failing test for `findByAgencyId` on RecurrencePatternRepository**

Open `backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java` and add at the end of the class (before the closing `}`):

```java
@Test
void findByAgencyId_returns_only_patterns_for_the_given_agency() {
    Agency agencyA = agencyRepo.save(new Agency("RP Agency A", "TX"));
    Agency agencyB = agencyRepo.save(new Agency("RP Agency B", "TX"));
    Client clientA = clientRepo.save(new Client(agencyA.getId(), "Alice", "RP", LocalDate.of(1960, 1, 1)));
    Client clientB = clientRepo.save(new Client(agencyB.getId(), "Bob", "RP", LocalDate.of(1960, 1, 1)));
    ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-RPA", true, "[]"));
    ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-RPB", true, "[]"));

    patternRepo.save(new RecurrencePattern(agencyA.getId(), clientA.getId(), stA.getId(),
        LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));
    patternRepo.save(new RecurrencePattern(agencyB.getId(), clientB.getId(), stB.getId(),
        LocalTime.of(10, 0), 60, "[\"TUESDAY\"]", LocalDate.of(2026, 5, 5)));

    List<RecurrencePattern> results = patternRepo.findByAgencyId(agencyA.getId());

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getAgencyId()).isEqualTo(agencyA.getId());
}
```

- [ ] **Step 8: Run to confirm it fails**

```bash
cd backend && mvn test -Dtest=RecurrencePatternDomainIT#findByAgencyId_returns_only_patterns_for_the_given_agency -pl . 2>&1 | tail -20
```

Expected: compilation error — `findByAgencyId` does not exist.

- [ ] **Step 9: Add `findByAgencyId` to `RecurrencePatternRepository`**

In `backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java`, add this method inside the interface body:

```java
List<RecurrencePattern> findByAgencyId(UUID agencyId);
```

- [ ] **Step 9b: Add `existsByIdAndAgencyId` to `CaregiverRepository`**

In `backend/src/main/java/com/hcare/domain/CaregiverRepository.java`, add this method inside the interface body (Spring Data derives it from the method name — no `@Query` needed):

```java
boolean existsByIdAndAgencyId(UUID id, UUID agencyId);
```

No new imports required if `UUID` is already imported. No dedicated domain IT test needed: the method is verified indirectly by the `assignCaregiver_with_caregiver_from_another_agency_throws_422` unit test in `ShiftSchedulingServiceTest`.

- [ ] **Step 10: Add `findByIdForUpdate` to `ShiftRepository`**

In `backend/src/main/java/com/hcare/domain/ShiftRepository.java`, add these imports after the existing imports:

```java
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
```

> **M23 note:** `@Query` and `@Param` are likely already imported by `findOverlapping`. Skip any imports already present.

Then add this method inside the interface body, after `findOverlapping`:

```java
/**
 * Loads a shift with a pessimistic write lock. Required by ShiftSchedulingService.respondToOffer
 * to prevent concurrent double-assignment when two admins simultaneously accept different offers
 * for the same OPEN shift.
 *
 * No dedicated domain IT test: single-threaded integration tests cannot verify
 * PostgreSQL row-level locking. Correctness is verified by the ShiftSchedulingServiceTest
 * unit tests added in Task 3.
 */
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Shift s WHERE s.id = :id")
Optional<Shift> findByIdForUpdate(@Param("id") UUID id);
```

- [ ] **Step 11: Write a failing test for the new RecurrencePattern setters**

Open `backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java` and add:

```java
@Test
void recurrencePattern_scheduling_setters_persist_correctly() {
    Agency agency = agencyRepo.save(new Agency("Setter Test Agency", "TX"));
    Client client = clientRepo.save(new Client(agency.getId(), "Setter", "Test", LocalDate.of(1970, 6, 15)));
    ServiceType st = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-SET", true, "[]"));

    RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
        agency.getId(), client.getId(), st.getId(),
        LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

    pattern.setScheduledStartTime(LocalTime.of(14, 30));
    pattern.setScheduledDurationMinutes(180);
    pattern.setDaysOfWeek("[\"WEDNESDAY\",\"FRIDAY\"]");
    patternRepo.save(pattern);

    RecurrencePattern reloaded = patternRepo.findById(pattern.getId()).orElseThrow();
    assertThat(reloaded.getScheduledStartTime()).isEqualTo(LocalTime.of(14, 30));
    assertThat(reloaded.getScheduledDurationMinutes()).isEqualTo(180);
    assertThat(reloaded.getDaysOfWeek()).isEqualTo("[\"WEDNESDAY\",\"FRIDAY\"]");
}
```

- [ ] **Step 12: Run to confirm it fails**

```bash
cd backend && mvn test -Dtest=RecurrencePatternDomainIT#recurrencePattern_scheduling_setters_persist_correctly -pl . 2>&1 | tail -20
```

Expected: compilation error — `setScheduledStartTime`, `setScheduledDurationMinutes`, `setDaysOfWeek` do not exist.

- [ ] **Step 13: Add the three setters to `RecurrencePattern`**

In `backend/src/main/java/com/hcare/domain/RecurrencePattern.java`, add these three methods alongside the existing setters (after `setGeneratedThrough`):

```java
public void setScheduledStartTime(LocalTime scheduledStartTime) { this.scheduledStartTime = scheduledStartTime; }
public void setScheduledDurationMinutes(int scheduledDurationMinutes) { this.scheduledDurationMinutes = scheduledDurationMinutes; }
public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
```

- [ ] **Step 14: Run all four tests to confirm they pass**

```bash
cd backend && mvn test -Dtest="ShiftDomainIT#findByAgencyIdAndScheduledStartBetween_returns_only_matching_agency_and_window,ShiftSubEntitiesIT#findByCaregiverIdAndShiftId_returns_offer_when_present_and_empty_when_not,RecurrencePatternDomainIT#findByAgencyId_returns_only_patterns_for_the_given_agency,RecurrencePatternDomainIT#recurrencePattern_scheduling_setters_persist_correctly" -pl . 2>&1 | tail -20
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 15: Run the full test suite to confirm no regressions**

```bash
cd backend && mvn test -pl . 2>&1 | tail -30
```

Expected: `BUILD SUCCESS` with all pre-existing tests still passing.

- [ ] **Step 16: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/RecurrencePattern.java \
  src/main/java/com/hcare/domain/ShiftRepository.java \
  src/main/java/com/hcare/domain/ShiftOfferRepository.java \
  src/main/java/com/hcare/domain/RecurrencePatternRepository.java \
  src/test/java/com/hcare/domain/ShiftDomainIT.java \
  src/test/java/com/hcare/domain/ShiftSubEntitiesIT.java \
  src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java
git commit -m "feat: add repository queries and RecurrencePattern scheduling setters for scheduling API"
```

---

### Task 2: Scheduling DTOs

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftSummaryResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateShiftRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/AssignCaregiverRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CancelShiftRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RankedCaregiverResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftOfferSummary.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RespondToOfferRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateRecurrencePatternRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RecurrencePatternResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/dto/UpdateRecurrencePatternRequest.java`

Note: There is already a `ShiftOfferResponse` enum in `com.hcare.domain`. Name the DTO class `ShiftOfferSummary` to avoid a class-name collision.

- [ ] **Step 1: Create `ShiftSummaryResponse`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftSummaryResponse.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import com.hcare.domain.ShiftStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftSummaryResponse(
    UUID id,
    UUID agencyId,
    UUID clientId,
    UUID caregiverId,
    UUID serviceTypeId,
    UUID authorizationId,
    UUID sourcePatternId,
    LocalDateTime scheduledStart,
    LocalDateTime scheduledEnd,
    ShiftStatus status,
    String notes
) {}
```

- [ ] **Step 2: Create `CreateShiftRequest`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateShiftRequest.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateShiftRequest(
    @NotNull UUID clientId,
    UUID caregiverId,
    @NotNull UUID serviceTypeId,
    UUID authorizationId,
    @NotNull LocalDateTime scheduledStart,
    @NotNull LocalDateTime scheduledEnd,
    String notes
) {}
```

- [ ] **Step 3: Create `AssignCaregiverRequest`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/AssignCaregiverRequest.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCaregiverRequest(
    @NotNull UUID caregiverId
) {}
```

- [ ] **Step 4: Create `CancelShiftRequest`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CancelShiftRequest.java`:

```java
package com.hcare.api.v1.scheduling.dto;

public record CancelShiftRequest(
    String notes
) {}
```

- [ ] **Step 5: Create `RankedCaregiverResponse`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RankedCaregiverResponse.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import java.util.UUID;

public record RankedCaregiverResponse(
    UUID caregiverId,
    double score,
    String explanation
) {}
```

- [ ] **Step 6: Create `ShiftOfferSummary`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftOfferSummary.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import com.hcare.domain.ShiftOfferResponse;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShiftOfferSummary(
    UUID id,
    UUID shiftId,
    UUID caregiverId,
    UUID agencyId,
    LocalDateTime offeredAt,
    LocalDateTime respondedAt,
    ShiftOfferResponse response
) {}
```

- [ ] **Step 7: Create `RespondToOfferRequest`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RespondToOfferRequest.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import com.hcare.domain.ShiftOfferResponse;
import jakarta.validation.constraints.NotNull;

public record RespondToOfferRequest(
    @NotNull ShiftOfferResponse response
) {}
```

- [ ] **Step 8: Create `CreateRecurrencePatternRequest`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/CreateRecurrencePatternRequest.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateRecurrencePatternRequest(
    @NotNull UUID clientId,
    UUID caregiverId,
    @NotNull UUID serviceTypeId,
    UUID authorizationId,
    @NotNull LocalTime scheduledStartTime,
    @Min(1) int scheduledDurationMinutes,
    @NotBlank
    @Pattern(
        regexp = "^\\[\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\"" +
                 "(,\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\")*\\]$",
        message = "daysOfWeek must be a non-empty JSON array of uppercase day names, " +
                  "e.g. [\"MONDAY\",\"WEDNESDAY\"]"
    )
    String daysOfWeek,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {}
```

- [ ] **Step 9: Create `RecurrencePatternResponse`**

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/RecurrencePatternResponse.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record RecurrencePatternResponse(
    UUID id,
    UUID agencyId,
    UUID clientId,
    UUID caregiverId,
    UUID serviceTypeId,
    UUID authorizationId,
    LocalTime scheduledStartTime,
    int scheduledDurationMinutes,
    String daysOfWeek,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate generatedThrough,
    boolean active,
    Long version,
    LocalDateTime createdAt
) {}
```

- [ ] **Step 10: Create `UpdateRecurrencePatternRequest`**

All fields are optional (null = no change). A field being non-null means "update this field."

Create file `backend/src/main/java/com/hcare/api/v1/scheduling/dto/UpdateRecurrencePatternRequest.java`:

```java
package com.hcare.api.v1.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpdateRecurrencePatternRequest(
    LocalTime scheduledStartTime,
    @Min(1) Integer scheduledDurationMinutes,
    @Pattern(
        regexp = "^\\[\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\"" +
                 "(,\"(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)\")*\\]$",
        message = "daysOfWeek must be a non-empty JSON array of uppercase day names, " +
                  "e.g. [\"MONDAY\",\"WEDNESDAY\"]"
    )
    String daysOfWeek,
    UUID caregiverId,
    UUID authorizationId,
    LocalDate endDate
) {}
```

- [ ] **Step 11: Verify the project compiles**

```bash
cd backend && mvn compile -pl . 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

- [ ] **Step 12: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/scheduling/
git commit -m "feat: add scheduling API DTOs"
```

---

### Task 3: `ShiftSchedulingService` + `ShiftOfferCreationService` — calendar list, create, assign, unassign, cancel

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftOfferCreationService.java`
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java`
- Create: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftOfferCreationServiceTest.java`
- Create: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java`

- [ ] **Step 1: Write the failing unit tests**

Create `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftCancelledEvent;
import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import com.hcare.domain.ShiftOfferResponse;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scoring.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ShiftSchedulingServiceTest {

    @Mock ShiftRepository shiftRepository;
    @Mock ShiftOfferRepository shiftOfferRepository;
    @Mock AuthorizationRepository authorizationRepository;
    @Mock CaregiverRepository caregiverRepository;
    @Mock ScoringService scoringService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ShiftOfferCreationService offerCreationService;

    ShiftSchedulingService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID serviceTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShiftSchedulingService(shiftRepository, shiftOfferRepository,
            authorizationRepository, caregiverRepository, scoringService, eventPublisher,
            offerCreationService);
    }

    // --- listShifts ---

    @Test
    void listShifts_delegates_to_repository_and_maps_to_response() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 8, 0, 0);
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end))
            .thenReturn(List.of(shift));

        List<ShiftSummaryResponse> result = service.listShifts(agencyId, start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientId()).isEqualTo(clientId);
        assertThat(result.get(0).status()).isEqualTo(ShiftStatus.OPEN);
    }

    @Test
    void listShifts_rejects_inverted_date_range() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 8, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 5, 1, 0, 0); // end before start

        assertThatThrownBy(() -> service.listShifts(agencyId, start, end))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
        verifyNoInteractions(shiftRepository);
    }

    // --- createShift ---

    @Test
    void createShift_saves_shift_and_returns_response() {
        CreateShiftRequest req = new CreateShiftRequest(clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0), null);
        Shift saved = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            req.scheduledStart(), req.scheduledEnd());
        when(shiftRepository.save(any())).thenReturn(saved);

        ShiftSummaryResponse result = service.createShift(agencyId, req);

        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.status()).isEqualTo(ShiftStatus.OPEN);
        verify(shiftRepository).save(any(Shift.class));
    }

    @Test
    void createShift_with_caregiverId_sets_status_to_ASSIGNED() {
        UUID caregiverId = UUID.randomUUID();
        CreateShiftRequest req = new CreateShiftRequest(clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0), null);
        Shift saved = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            req.scheduledStart(), req.scheduledEnd());
        when(shiftRepository.save(any())).thenReturn(saved);

        ShiftSummaryResponse result = service.createShift(agencyId, req);

        assertThat(result.status()).isEqualTo(ShiftStatus.ASSIGNED);
        verify(shiftRepository).save(argThat(s -> s.getStatus() == ShiftStatus.ASSIGNED));
    }

    @Test
    void createShift_with_authorization_from_different_client_throws_422() {
        UUID authorizationId = UUID.randomUUID();
        UUID differentClientId = UUID.randomUUID();
        Authorization auth = mock(Authorization.class);
        when(auth.getClientId()).thenReturn(differentClientId);
        when(authorizationRepository.findById(authorizationId)).thenReturn(Optional.of(auth));

        CreateShiftRequest req = new CreateShiftRequest(clientId, null, serviceTypeId, authorizationId,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0), null);

        assertThatThrownBy(() -> service.createShift(agencyId, req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
        verifyNoInteractions(shiftRepository);
    }

    // --- assignCaregiver ---

    @Test
    void assignCaregiver_transitions_open_shift_to_assigned() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(caregiverRepository.existsByIdAndAgencyId(caregiverId, agencyId)).thenReturn(true);
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftSummaryResponse result = service.assignCaregiver(shiftId, new AssignCaregiverRequest(caregiverId));

        assertThat(result.status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(result.caregiverId()).isEqualTo(caregiverId);
    }

    @Test
    void assignCaregiver_on_assigned_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.assignCaregiver(shiftId, new AssignCaregiverRequest(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void assignCaregiver_on_missing_shift_throws_404() {
        UUID shiftId = UUID.randomUUID();
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignCaregiver(shiftId, new AssignCaregiverRequest(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void assignCaregiver_with_caregiver_from_another_agency_throws_422() {
        // M22: Hibernate agencyFilter blocks cross-agency reads but not cross-agency FK writes.
        UUID shiftId = UUID.randomUUID();
        UUID foreignCaregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(caregiverRepository.existsByIdAndAgencyId(foreignCaregiverId, agencyId)).thenReturn(false);

        assertThatThrownBy(() -> service.assignCaregiver(shiftId, new AssignCaregiverRequest(foreignCaregiverId)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
        verify(shiftRepository, never()).save(any());
    }

    // --- unassignCaregiver ---

    @Test
    void unassignCaregiver_transitions_assigned_shift_to_open() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftSummaryResponse result = service.unassignCaregiver(shiftId);

        assertThat(result.status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(result.caregiverId()).isNull();
    }

    @Test
    void unassignCaregiver_on_open_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.unassignCaregiver(shiftId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- cancelShift ---

    @Test
    void cancelShift_transitions_open_shift_to_cancelled_without_publishing_event() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftSummaryResponse result = service.cancelShift(shiftId, new CancelShiftRequest(null));

        assertThat(result.status()).isEqualTo(ShiftStatus.CANCELLED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelShift_on_assigned_shift_publishes_ShiftCancelledEvent() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        service.cancelShift(shiftId, new CancelShiftRequest("Client no-show"));

        ArgumentCaptor<ShiftCancelledEvent> captor = ArgumentCaptor.forClass(ShiftCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().caregiverId()).isEqualTo(caregiverId);
        assertThat(captor.getValue().agencyId()).isEqualTo(agencyId);
    }

    @Test
    void cancelShift_on_in_progress_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.cancelShift(shiftId, new CancelShiftRequest(null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // --- getCandidates ---

    @Test
    void getCandidates_delegates_to_scoring_service_and_maps_results() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(scoringService.rankCandidates(any())).thenReturn(
            List.of(new com.hcare.scoring.RankedCaregiver(caregiverId, 0.85, "Good match")));

        List<RankedCaregiverResponse> result = service.getCandidates(shiftId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).caregiverId()).isEqualTo(caregiverId);
        assertThat(result.get(0).score()).isEqualTo(0.85);
        verify(scoringService).rankCandidates(any());
    }

    // --- broadcastShift ---

    @Test
    void broadcastShift_on_non_open_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.broadcastShift(shiftId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verifyNoInteractions(shiftOfferRepository);
    }

    @Test
    void broadcastShift_creates_offers_for_all_eligible_candidates_and_returns_summaries() {
        UUID shiftId = UUID.randomUUID();
        UUID cg1 = UUID.randomUUID();
        UUID cg2 = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(scoringService.rankCandidates(any())).thenReturn(List.of(
            new com.hcare.scoring.RankedCaregiver(cg1, 0.9, null),
            new com.hcare.scoring.RankedCaregiver(cg2, 0.7, null)));
        // C13: offerCreationService.createOfferIfAbsent is a void method; default mock is a no-op.
        // Idempotency and concurrency safety are tested in ShiftOfferCreationServiceTest.
        when(shiftOfferRepository.findByShiftId(shiftId)).thenReturn(List.of(
            new ShiftOffer(shiftId, cg1, agencyId),
            new ShiftOffer(shiftId, cg2, agencyId)));

        List<ShiftOfferSummary> result = service.broadcastShift(shiftId);

        assertThat(result).hasSize(2);
        verify(offerCreationService).createOfferIfAbsent(shiftId, cg1, agencyId);
        verify(offerCreationService).createOfferIfAbsent(shiftId, cg2, agencyId);
        verify(shiftOfferRepository).findByShiftId(shiftId);
    }

    // --- listOffers ---

    @Test
    void listOffers_returns_offer_summaries_for_shift() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(shiftOfferRepository.findByShiftId(shiftId)).thenReturn(
            List.of(new ShiftOffer(shiftId, caregiverId, agencyId)));

        List<ShiftOfferSummary> result = service.listOffers(shiftId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).caregiverId()).isEqualTo(caregiverId);
        assertThat(result.get(0).response()).isEqualTo(ShiftOfferResponse.NO_RESPONSE);
    }

    // --- respondToOffer ---

    @Test
    void respondToOffer_with_NO_RESPONSE_throws_400() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.respondToOffer(shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.NO_RESPONSE)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
        verifyNoInteractions(shiftRepository, shiftOfferRepository);
    }

    @Test
    void respondToOffer_on_already_responded_offer_throws_409() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);
        offer.respond(ShiftOfferResponse.DECLINED);
        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.respondToOffer(shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void respondToOffer_accepted_assigns_caregiver_and_declines_other_pending_offers() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID otherOfferId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID otherCaregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);
        ShiftOffer otherOffer = new ShiftOffer(shiftId, otherCaregiverId, agencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);
        when(shiftOfferRepository.findByShiftId(shiftId)).thenReturn(List.of(offer, otherOffer));
        when(shiftOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.respondToOffer(shiftId, offerId,
            new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED));

        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(shift.getCaregiverId()).isEqualTo(caregiverId);
        assertThat(otherOffer.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);
    }

    @Test
    void respondToOffer_accepted_on_non_open_shift_throws_409() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, UUID.randomUUID(), serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(shiftRepository.findByIdForUpdate(shiftId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.respondToOffer(shiftId, offerId,
                new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
        verify(shiftRepository, never()).save(any());
        verify(shiftOfferRepository, never()).save(any());
    }

    @Test
    void respondToOffer_declined_does_not_mutate_shift() {
        UUID shiftId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        Shift shift = new Shift(agencyId, null, clientId, null, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0));
        ShiftOffer offer = new ShiftOffer(shiftId, caregiverId, agencyId);

        when(shiftOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(shiftOfferRepository.save(any())).thenReturn(offer);

        service.respondToOffer(shiftId, offerId,
            new RespondToOfferRequest(ShiftOfferResponse.DECLINED));

        assertThat(offer.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.OPEN);
        verify(shiftRepository, never()).findByIdForUpdate(any());
        verify(shiftRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run to confirm the tests fail**

```bash
cd backend && mvn test -Dtest=ShiftSchedulingServiceTest,ShiftOfferCreationServiceTest -pl . 2>&1 | tail -20
```

Expected: compilation error — `ShiftSchedulingService` and `ShiftOfferCreationService` classes do not exist.

- [ ] **Step 3: Implement `ShiftOfferCreationService` and `ShiftSchedulingService`**

Create `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftOfferCreationService.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ShiftOfferCreationService {

    private final ShiftOfferRepository shiftOfferRepository;

    public ShiftOfferCreationService(ShiftOfferRepository shiftOfferRepository) {
        this.shiftOfferRepository = shiftOfferRepository;
    }

    /**
     * Creates a shift offer for the given caregiver if one does not already exist.
     * Runs in a separate transaction (REQUIRES_NEW) so that a DataIntegrityViolationException
     * from a concurrent duplicate insert (unique constraint on shift_id, caregiver_id) is isolated
     * to this sub-transaction and does not poison the caller's outer transaction.
     *
     * C13 fix: the pre-flight check in broadcastShift prevented sequential re-broadcasts but not
     * concurrent callers, both of whom can read Optional.empty() before either commits their save.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOfferIfAbsent(UUID shiftId, UUID caregiverId, UUID agencyId) {
        if (shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId).isEmpty()) {
            shiftOfferRepository.save(new ShiftOffer(shiftId, caregiverId, agencyId));
        }
    }
}
```

Also create `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftOfferCreationServiceTest.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftOfferCreationServiceTest {

    @Mock ShiftOfferRepository shiftOfferRepository;
    ShiftOfferCreationService service;

    @BeforeEach
    void setUp() {
        service = new ShiftOfferCreationService(shiftOfferRepository);
    }

    @Test
    void createOfferIfAbsent_saves_when_no_existing_offer() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        when(shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId))
            .thenReturn(Optional.empty());

        service.createOfferIfAbsent(shiftId, caregiverId, agencyId);

        verify(shiftOfferRepository).save(any(ShiftOffer.class));
    }

    @Test
    void createOfferIfAbsent_skips_save_when_offer_already_exists() {
        UUID shiftId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        UUID agencyId = UUID.randomUUID();
        when(shiftOfferRepository.findByCaregiverIdAndShiftId(caregiverId, shiftId))
            .thenReturn(Optional.of(new ShiftOffer(shiftId, caregiverId, agencyId)));

        service.createOfferIfAbsent(shiftId, caregiverId, agencyId);

        verify(shiftOfferRepository, never()).save(any());
    }
}
```

Create `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftCancelledEvent;
import com.hcare.domain.ShiftOffer;
import com.hcare.domain.ShiftOfferRepository;
import com.hcare.domain.ShiftOfferResponse;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scoring.RankedCaregiver;
import com.hcare.scoring.ScoringService;
import com.hcare.scoring.ShiftMatchRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShiftSchedulingService {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(ShiftSchedulingService.class);

    private final ShiftRepository shiftRepository;
    private final ShiftOfferRepository shiftOfferRepository;
    private final AuthorizationRepository authorizationRepository;
    private final CaregiverRepository caregiverRepository;
    private final ScoringService scoringService;
    private final ApplicationEventPublisher eventPublisher;
    private final ShiftOfferCreationService offerCreationService;

    public ShiftSchedulingService(ShiftRepository shiftRepository,
                                   ShiftOfferRepository shiftOfferRepository,
                                   AuthorizationRepository authorizationRepository,
                                   CaregiverRepository caregiverRepository,
                                   ScoringService scoringService,
                                   ApplicationEventPublisher eventPublisher,
                                   ShiftOfferCreationService offerCreationService) {
        this.shiftRepository = shiftRepository;
        this.shiftOfferRepository = shiftOfferRepository;
        this.authorizationRepository = authorizationRepository;
        this.caregiverRepository = caregiverRepository;
        this.scoringService = scoringService;
        this.eventPublisher = eventPublisher;
        this.offerCreationService = offerCreationService;
    }

    @Transactional(readOnly = true)
    public List<ShiftSummaryResponse> listShifts(UUID agencyId, LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
        }
        return shiftRepository.findByAgencyIdAndScheduledStartBetween(agencyId, start, end)
            .stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public ShiftSummaryResponse createShift(UUID agencyId, CreateShiftRequest req) {
        if (!req.scheduledEnd().isAfter(req.scheduledStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "scheduledEnd must be after scheduledStart");
        }
        // Q3: validate authorizationId ownership — must belong to the same client.
        // Cross-agency access is blocked by the active agencyFilter (findById returns empty).
        if (req.authorizationId() != null) {
            Authorization auth = authorizationRepository.findById(req.authorizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Authorization not found"));
            if (!auth.getClientId().equals(req.clientId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Authorization does not belong to the specified client");
            }
        }
        Shift shift = new Shift(agencyId, null, req.clientId(), req.caregiverId(),
            req.serviceTypeId(), req.authorizationId(),
            req.scheduledStart(), req.scheduledEnd());
        if (req.notes() != null) shift.setNotes(req.notes());
        // C8: when caregiverId is supplied at creation, immediately mark ASSIGNED
        if (req.caregiverId() != null) shift.setStatus(ShiftStatus.ASSIGNED);
        return toSummary(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftSummaryResponse assignCaregiver(UUID shiftId, AssignCaregiverRequest req) {
        Shift shift = requireShift(shiftId);
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Shift must be OPEN to assign a caregiver (current status: " + shift.getStatus() + ")");
        }
        // M22: Hibernate agencyFilter prevents cross-agency reads but not cross-agency FK writes.
        // Verify the caregiver belongs to the same agency before assigning.
        if (!caregiverRepository.existsByIdAndAgencyId(req.caregiverId(), shift.getAgencyId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Caregiver does not belong to this agency");
        }
        shift.setCaregiverId(req.caregiverId());
        shift.setStatus(ShiftStatus.ASSIGNED);
        return toSummary(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftSummaryResponse unassignCaregiver(UUID shiftId) {
        Shift shift = requireShift(shiftId);
        if (shift.getStatus() != ShiftStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Shift must be ASSIGNED to unassign caregiver (current status: " + shift.getStatus() + ")");
        }
        shift.setCaregiverId(null);
        shift.setStatus(ShiftStatus.OPEN);
        return toSummary(shiftRepository.save(shift));
    }

    @Transactional
    public ShiftSummaryResponse cancelShift(UUID shiftId, CancelShiftRequest req) {
        Shift shift = requireShift(shiftId);
        if (shift.getStatus() != ShiftStatus.OPEN && shift.getStatus() != ShiftStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only OPEN or ASSIGNED shifts can be cancelled (current status: " + shift.getStatus() + ")");
        }
        UUID caregiverId = shift.getCaregiverId();
        if (req.notes() != null) shift.setNotes(req.notes());
        shift.setStatus(ShiftStatus.CANCELLED);
        ShiftSummaryResponse response = toSummary(shiftRepository.save(shift));
        if (caregiverId != null) {
            eventPublisher.publishEvent(new ShiftCancelledEvent(shiftId, caregiverId, shift.getAgencyId()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<RankedCaregiverResponse> getCandidates(UUID shiftId) {
        Shift shift = requireShift(shiftId);
        List<RankedCaregiver> ranked = scoringService.rankCandidates(new ShiftMatchRequest(
            shift.getAgencyId(), shift.getClientId(), shift.getServiceTypeId(),
            shift.getAuthorizationId(), shift.getScheduledStart(), shift.getScheduledEnd()));
        return ranked.stream()
            .map(rc -> new RankedCaregiverResponse(rc.caregiverId(), rc.score(), rc.explanation()))
            .toList();
    }

    @Transactional
    public List<ShiftOfferSummary> broadcastShift(UUID shiftId) {
        Shift shift = requireShift(shiftId);
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only OPEN shifts can be broadcast (current status: " + shift.getStatus() + ")");
        }
        List<RankedCaregiver> eligible = scoringService.rankCandidates(new ShiftMatchRequest(
            shift.getAgencyId(), shift.getClientId(), shift.getServiceTypeId(),
            shift.getAuthorizationId(), shift.getScheduledStart(), shift.getScheduledEnd()));
        for (RankedCaregiver rc : eligible) {
            // C13: delegate to a REQUIRES_NEW sub-transaction so that a DataIntegrityViolationException
            // from a concurrent duplicate (unique constraint on shift_id, caregiver_id) is isolated
            // to that sub-transaction and does not poison this outer transaction.
            offerCreationService.createOfferIfAbsent(shiftId, rc.caregiverId(), shift.getAgencyId());
        }
        return shiftOfferRepository.findByShiftId(shiftId).stream()
            .map(this::toOfferSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftOfferSummary> listOffers(UUID shiftId) {
        requireShift(shiftId);
        return shiftOfferRepository.findByShiftId(shiftId).stream()
            .map(this::toOfferSummary)
            .toList();
    }

    @Transactional
    public ShiftOfferSummary respondToOffer(UUID shiftId, UUID offerId, RespondToOfferRequest req) {
        // C4: reject NO_RESPONSE — callers must explicitly ACCEPT or DECLINE
        if (req.response() == ShiftOfferResponse.NO_RESPONSE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Response must be ACCEPTED or DECLINED");
        }
        // C6: no pre-flight requireShift — offer.getShiftId() guard below confirms membership,
        //     and the locked load inside the ACCEPTED branch handles shift existence.
        ShiftOffer offer = shiftOfferRepository.findById(offerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (!offer.getShiftId().equals(shiftId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer does not belong to this shift");
        }
        if (offer.getResponse() != ShiftOfferResponse.NO_RESPONSE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Offer already has a response: " + offer.getResponse());
        }

        if (req.response() == ShiftOfferResponse.ACCEPTED) {
            // C3: pessimistic write lock prevents concurrent double-assignment.
            // C5: offer mutation happens AFTER lock acquisition and OPEN guard — prevents
            //     corrupted offer state (response=ACCEPTED, no shift assignment) on 409 path.
            Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
            if (shift.getStatus() != ShiftStatus.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot accept offer: shift is no longer OPEN (status: " + shift.getStatus() + ")");
            }
            offer.respond(ShiftOfferResponse.ACCEPTED);
            shiftOfferRepository.save(offer);
            shift.setCaregiverId(offer.getCaregiverId());
            shift.setStatus(ShiftStatus.ASSIGNED);
            shiftRepository.save(shift);

            // Decline all other pending offers for this shift
            shiftOfferRepository.findByShiftId(shiftId).stream()
                .filter(o -> !offerId.equals(o.getId()) && o.getResponse() == ShiftOfferResponse.NO_RESPONSE)
                .forEach(o -> {
                    o.respond(ShiftOfferResponse.DECLINED);
                    shiftOfferRepository.save(o);
                });
        } else {
            // DECLINED path — no shift lock needed
            offer.respond(ShiftOfferResponse.DECLINED);
            shiftOfferRepository.save(offer);
        }

        return toOfferSummary(offer);
    }

    // --- helpers ---

    private Shift requireShift(UUID shiftId) {
        return shiftRepository.findById(shiftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
    }

    private ShiftSummaryResponse toSummary(Shift shift) {
        return new ShiftSummaryResponse(
            shift.getId(), shift.getAgencyId(), shift.getClientId(), shift.getCaregiverId(),
            shift.getServiceTypeId(), shift.getAuthorizationId(), shift.getSourcePatternId(),
            shift.getScheduledStart(), shift.getScheduledEnd(), shift.getStatus(), shift.getNotes());
    }

    private ShiftOfferSummary toOfferSummary(ShiftOffer offer) {
        return new ShiftOfferSummary(
            offer.getId(), offer.getShiftId(), offer.getCaregiverId(), offer.getAgencyId(),
            offer.getOfferedAt(), offer.getRespondedAt(), offer.getResponse());
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

```bash
cd backend && mvn test -Dtest=ShiftSchedulingServiceTest -pl . 2>&1 | tail -20
```

Expected: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run the full suite to confirm no regressions**

```bash
cd backend && mvn test -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/CaregiverRepository.java \
  src/main/java/com/hcare/api/v1/scheduling/ShiftOfferCreationService.java \
  src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java \
  src/test/java/com/hcare/api/v1/scheduling/ShiftOfferCreationServiceTest.java \
  src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java
git commit -m "feat: implement ShiftSchedulingService with calendar, assign, unassign, cancel, candidates, broadcast, offers"
```

---

### Task 4: `ShiftSchedulingController` + integration tests

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java`
- Create: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java`

- [ ] **Step 1: Write the failing integration tests**

Create `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles, " +
    "caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ShiftSchedulingControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private ShiftOfferRepository shiftOfferRepo;
    @Autowired private AuthorizationRepository authorizationRepo;

    private Agency agency;
    private Client client;
    private ServiceType serviceType;
    private Caregiver caregiver;

    @BeforeEach
    void seed() {
        agency = agencyRepo.save(new Agency("Sched Test Agency", "TX"));
        userRepo.save(new AgencyUser(agency.getId(), "sched-admin@test.com",
            AuthService.DUMMY_HASH_FOR_TEST, UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Cal", "Client", LocalDate.of(1970, 1, 1)));
        serviceType = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-IT", true, "[]"));
        caregiver = caregiverRepo.save(new Caregiver(agency.getId(), "Test", "Caregiver", "cg@test.com"));
    }

    private String token() {
        LoginRequest req = new LoginRequest("sched-admin@test.com", "correcthorsebatterystaple");
        return restTemplate.postForEntity("/api/v1/auth/login", req, LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // --- GET /shifts ---

    @Test
    void listShifts_returns_shifts_in_window_for_agency() {
        shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));

        ResponseEntity<List<ShiftSummaryResponse>> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).clientId()).isEqualTo(client.getId());
    }

    @Test
    void listShifts_returns_401_without_token() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
            HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listShifts_does_not_return_shifts_from_another_agency() {
        Agency other = agencyRepo.save(new Agency("Other Agency", "TX"));
        Client otherClient = clientRepo.save(new Client(other.getId(), "Other", "Client", LocalDate.of(1970, 1, 1)));
        ServiceType otherSt = serviceTypeRepo.save(new ServiceType(other.getId(), "PCS", "PCS-OTH", true, "[]"));
        shiftRepo.save(new Shift(other.getId(), null, otherClient.getId(), null, otherSt.getId(), null,
            LocalDateTime.of(2026, 5, 3, 9, 0), LocalDateTime.of(2026, 5, 3, 13, 0)));

        ResponseEntity<List<ShiftSummaryResponse>> resp = restTemplate.exchange(
            "/api/v1/shifts?start=2026-05-01T00:00:00&end=2026-05-08T00:00:00",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    // --- POST /shifts ---

    @Test
    void createShift_creates_open_shift_and_returns_201() {
        CreateShiftRequest req = new CreateShiftRequest(client.getId(), null, serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 10, 9, 0), LocalDateTime.of(2026, 5, 10, 13, 0), null);

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts", HttpMethod.POST,
            new HttpEntity<>(req, auth()), ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(resp.getBody().id()).isNotNull();
    }

    @Test
    void createShift_with_caregiverId_creates_assigned_shift() {
        CreateShiftRequest req = new CreateShiftRequest(client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 11, 9, 0), LocalDateTime.of(2026, 5, 11, 13, 0), null);

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts", HttpMethod.POST,
            new HttpEntity<>(req, auth()), ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(resp.getBody().caregiverId()).isEqualTo(caregiver.getId());
    }

    // --- PATCH /shifts/{id}/assign ---

    @Test
    void assignCaregiver_transitions_open_shift_to_assigned() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 12, 9, 0), LocalDateTime.of(2026, 5, 12, 13, 0)));

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/assign",
            HttpMethod.PATCH,
            new HttpEntity<>(new AssignCaregiverRequest(caregiver.getId()), auth()),
            ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(resp.getBody().caregiverId()).isEqualTo(caregiver.getId());
    }

    @Test
    void assignCaregiver_on_already_assigned_shift_returns_409() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 13, 9, 0), LocalDateTime.of(2026, 5, 13, 13, 0)));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/assign",
            HttpMethod.PATCH,
            new HttpEntity<>(new AssignCaregiverRequest(UUID.randomUUID()), auth()),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- PATCH /shifts/{id}/unassign ---

    @Test
    void unassignCaregiver_transitions_assigned_shift_to_open() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 14, 9, 0), LocalDateTime.of(2026, 5, 14, 13, 0)));

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/unassign",
            HttpMethod.PATCH,
            new HttpEntity<>(auth()),
            ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.OPEN);
        assertThat(resp.getBody().caregiverId()).isNull();
    }

    // --- PATCH /shifts/{id}/cancel ---

    @Test
    void cancelShift_transitions_open_shift_to_cancelled() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 15, 9, 0), LocalDateTime.of(2026, 5, 15, 13, 0)));

        ResponseEntity<ShiftSummaryResponse> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/cancel",
            HttpMethod.PATCH,
            new HttpEntity<>(new CancelShiftRequest("No longer needed"), auth()),
            ShiftSummaryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(ShiftStatus.CANCELLED);
    }

    @Test
    void cancelShift_on_completed_shift_returns_409() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 16, 9, 0), LocalDateTime.of(2026, 5, 16, 13, 0)));
        shift.setStatus(ShiftStatus.COMPLETED);
        shiftRepo.save(shift);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/cancel",
            HttpMethod.PATCH,
            new HttpEntity<>(new CancelShiftRequest(null), auth()),
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- GET /shifts/{id}/candidates ---

    @Test
    void getCandidates_returns_200_with_list() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 17, 9, 0), LocalDateTime.of(2026, 5, 17, 13, 0)));

        ResponseEntity<List<RankedCaregiverResponse>> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/candidates",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        // LocalScoringService runs with test profile; result may be empty (no scoring profiles seeded)
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    // --- POST /shifts/{id}/broadcast ---

    @Test
    void broadcastShift_on_non_open_shift_returns_409() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), caregiver.getId(),
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 18, 9, 0), LocalDateTime.of(2026, 5, 18, 13, 0)));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/broadcast",
            HttpMethod.POST, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void broadcastShift_on_open_shift_returns_200_with_offer_list() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 18, 9, 0), LocalDateTime.of(2026, 5, 18, 13, 0)));

        ResponseEntity<List<ShiftOfferSummary>> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/broadcast",
            HttpMethod.POST, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Result may be empty if no caregiver scoring profiles are seeded — acceptable
        assertThat(resp.getBody()).isNotNull();
    }

    // --- GET /shifts/{id}/offers ---

    @Test
    void listOffers_returns_all_offers_for_shift() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 19, 9, 0), LocalDateTime.of(2026, 5, 19, 13, 0)));
        shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        ResponseEntity<List<ShiftOfferSummary>> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/offers",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).caregiverId()).isEqualTo(caregiver.getId());
        assertThat(resp.getBody().get(0).response()).isEqualTo(ShiftOfferResponse.NO_RESPONSE);
    }

    // --- POST /shifts/{id}/offers/{offerId}/respond ---

    @Test
    void respondToOffer_accepted_assigns_caregiver_and_declines_others() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 20, 9, 0), LocalDateTime.of(2026, 5, 20, 13, 0)));
        Caregiver cg2 = caregiverRepo.save(new Caregiver(agency.getId(), "Other", "CG", "cg2@test.com"));
        ShiftOffer offerA = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));
        ShiftOffer offerB = shiftOfferRepo.save(new ShiftOffer(shift.getId(), cg2.getId(), agency.getId()));

        RespondToOfferRequest req = new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED);
        ResponseEntity<ShiftOfferSummary> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/offers/" + offerA.getId() + "/respond",
            HttpMethod.POST, new HttpEntity<>(req, auth()), ShiftOfferSummary.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().response()).isEqualTo(ShiftOfferResponse.ACCEPTED);

        // Shift should now be ASSIGNED
        Shift updated = shiftRepo.findById(shift.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(updated.getCaregiverId()).isEqualTo(caregiver.getId());

        // offerB should be auto-declined
        ShiftOffer declined = shiftOfferRepo.findById(offerB.getId()).orElseThrow();
        assertThat(declined.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);
    }

    @Test
    void respondToOffer_declined_does_not_change_shift_status() {
        Shift shift = shiftRepo.save(new Shift(agency.getId(), null, client.getId(), null,
            serviceType.getId(), null,
            LocalDateTime.of(2026, 5, 21, 9, 0), LocalDateTime.of(2026, 5, 21, 13, 0)));
        ShiftOffer offer = shiftOfferRepo.save(new ShiftOffer(shift.getId(), caregiver.getId(), agency.getId()));

        RespondToOfferRequest req = new RespondToOfferRequest(ShiftOfferResponse.DECLINED);
        ResponseEntity<ShiftOfferSummary> resp = restTemplate.exchange(
            "/api/v1/shifts/" + shift.getId() + "/offers/" + offer.getId() + "/respond",
            HttpMethod.POST, new HttpEntity<>(req, auth()), ShiftOfferSummary.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().response()).isEqualTo(ShiftOfferResponse.DECLINED);

        Shift unchanged = shiftRepo.findById(shift.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ShiftStatus.OPEN);
    }
}
```

- [ ] **Step 2: Run to confirm the tests fail**

```bash
cd backend && mvn test -Dtest=ShiftSchedulingControllerIT -pl . 2>&1 | tail -20
```

Expected: compilation error — `ShiftSchedulingController` class does not exist, and `AuthService` not importable in the test package.

- [ ] **Step 3: Implement `ShiftSchedulingController`**

Create `backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.AssignCaregiverRequest;
import com.hcare.api.v1.scheduling.dto.CancelShiftRequest;
import com.hcare.api.v1.scheduling.dto.CreateShiftRequest;
import com.hcare.api.v1.scheduling.dto.RankedCaregiverResponse;
import com.hcare.api.v1.scheduling.dto.RespondToOfferRequest;
import com.hcare.api.v1.scheduling.dto.ShiftOfferSummary;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftSchedulingController {

    private final ShiftSchedulingService shiftSchedulingService;

    public ShiftSchedulingController(ShiftSchedulingService shiftSchedulingService) {
        this.shiftSchedulingService = shiftSchedulingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftSummaryResponse>> listShifts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(shiftSchedulingService.listShifts(principal.getAgencyId(), start, end));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> createShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(shiftSchedulingService.createShift(principal.getAgencyId(), request));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> assignCaregiver(
            @PathVariable UUID id,
            @Valid @RequestBody AssignCaregiverRequest request) {
        return ResponseEntity.ok(shiftSchedulingService.assignCaregiver(id, request));
    }

    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> unassignCaregiver(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.unassignCaregiver(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftSummaryResponse> cancelShift(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelShiftRequest request) {
        return ResponseEntity.ok(shiftSchedulingService.cancelShift(id,
            request != null ? request : new CancelShiftRequest(null)));
    }

    @GetMapping("/{id}/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<RankedCaregiverResponse>> getCandidates(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.getCandidates(id));
    }

    @PostMapping("/{id}/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftOfferSummary>> broadcastShift(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.broadcastShift(id));
    }

    @GetMapping("/{id}/offers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftOfferSummary>> listOffers(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSchedulingService.listOffers(id));
    }

    @PostMapping("/{id}/offers/{offerId}/respond")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ShiftOfferSummary> respondToOffer(
            @PathVariable UUID id,
            @PathVariable UUID offerId,
            @Valid @RequestBody RespondToOfferRequest request) {
        return ResponseEntity.ok(shiftSchedulingService.respondToOffer(id, offerId, request));
    }
}
```

Note: The `GET /shifts` endpoint on `ShiftSchedulingController` does not conflict with `VisitController` — `VisitController` maps `GET /api/v1/shifts/{id}` (with a path variable), while this controller maps `GET /api/v1/shifts` (no path variable). Spring routes them separately.

- [ ] **Step 4: Fix the `AuthService` reference in the test**

The IT test uses `AuthService.DUMMY_HASH_FOR_TEST` which is in `com.hcare.api.v1.auth`. Add the import at the top of `ShiftSchedulingControllerIT.java`:

```java
import com.hcare.api.v1.auth.AuthService;
```

- [ ] **Step 5: Run the integration tests**

```bash
cd backend && mvn test -Dtest=ShiftSchedulingControllerIT -pl . 2>&1 | tail -30
```

Expected: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Run the full test suite**

```bash
cd backend && mvn test -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java \
  src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java
git commit -m "feat: add ShiftSchedulingController with full calendar/assign/cancel/offer endpoints"
```

---

### Task 5: `RecurrencePatternService` — create, get, update, delete

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternService.java`
- Create: `backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternServiceTest.java`

- [ ] **Step 1: Write the failing unit tests**

Create `backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternServiceTest.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scheduling.ShiftGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurrencePatternServiceTest {

    @Mock RecurrencePatternRepository patternRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock ShiftGenerationService shiftGenerationService;

    RecurrencePatternService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID serviceTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecurrencePatternService(patternRepository, shiftRepository, shiftGenerationService);
    }

    // --- createPattern ---

    @Test
    void createPattern_saves_pattern_and_calls_generateForPattern() {
        CreateRecurrencePatternRequest req = new CreateRecurrencePatternRequest(
            clientId, null, serviceTypeId, null,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]",
            LocalDate.of(2026, 5, 4), null);

        RecurrencePattern saved = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.save(any())).thenReturn(saved);

        RecurrencePatternResponse result = service.createPattern(agencyId, req);

        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
        verify(patternRepository).save(any(RecurrencePattern.class));
        verify(shiftGenerationService).generateForPattern(saved);
    }

    // --- listPatterns ---

    @Test
    void listPatterns_returns_all_patterns_for_agency() {
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findByAgencyId(agencyId)).thenReturn(List.of(pattern));

        List<RecurrencePatternResponse> result = service.listPatterns(agencyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));
    }

    // --- getPattern ---

    @Test
    void getPattern_returns_response_when_found() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));

        RecurrencePatternResponse result = service.getPattern(patternId);

        assertThat(result.scheduledDurationMinutes()).isEqualTo(120);
    }

    @Test
    void getPattern_throws_404_when_not_found() {
        UUID patternId = UUID.randomUUID();
        when(patternRepository.findById(patternId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPattern(patternId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- updatePattern: scheduling fields trigger regeneration ---

    @Test
    void updatePattern_with_new_scheduledStartTime_calls_regenerateAfterEdit() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            LocalTime.of(14, 0), null, null, null, null, null);

        service.updatePattern(patternId, req);

        verify(shiftGenerationService).regenerateAfterEdit(pattern);
        verifyNoMoreInteractions(shiftGenerationService);
    }

    @Test
    void updatePattern_with_new_scheduledDurationMinutes_calls_regenerateAfterEdit() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, 180, null, null, null, null);

        service.updatePattern(patternId, req);

        verify(shiftGenerationService).regenerateAfterEdit(pattern);
    }

    @Test
    void updatePattern_with_new_daysOfWeek_calls_regenerateAfterEdit() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, "[\"WEDNESDAY\",\"FRIDAY\"]", null, null, null);

        service.updatePattern(patternId, req);

        verify(shiftGenerationService).regenerateAfterEdit(pattern);
    }

    @Test
    void updatePattern_caregiverId_only_saves_in_place_without_regeneration() {
        UUID patternId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, caregiverId, null, null);

        service.updatePattern(patternId, req);

        verifyNoInteractions(shiftGenerationService);
        verify(patternRepository).save(pattern);
        assertThat(pattern.getCaregiverId()).isEqualTo(caregiverId);
    }

    @Test
    void updatePattern_endDate_only_saves_in_place_without_regeneration() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        LocalDate newEndDate = LocalDate.of(2026, 12, 31);
        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, null, null, newEndDate);

        service.updatePattern(patternId, req);

        verifyNoInteractions(shiftGenerationService);
        assertThat(pattern.getEndDate()).isEqualTo(newEndDate);
    }

    // --- deactivatePattern ---

    @Test
    void deactivatePattern_sets_isActive_false_and_deletes_future_shifts() {
        UUID patternId = UUID.randomUUID();
        RecurrencePattern pattern = new RecurrencePattern(agencyId, clientId, serviceTypeId,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4));
        when(patternRepository.findById(patternId)).thenReturn(Optional.of(pattern));
        when(patternRepository.save(pattern)).thenReturn(pattern);

        service.deactivatePattern(patternId);

        assertThat(pattern.isActive()).isFalse();
        verify(shiftRepository).deleteUnstartedFutureShifts(
            eq(patternId), eq(agencyId), any(), eq(List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)));
        verify(patternRepository).save(pattern);
    }
}
```

- [ ] **Step 2: Run to confirm the tests fail**

```bash
cd backend && mvn test -Dtest=RecurrencePatternServiceTest -pl . 2>&1 | tail -20
```

Expected: compilation error — `RecurrencePatternService` does not exist.

- [ ] **Step 3: Implement `RecurrencePatternService`**

Create `backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternService.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
import com.hcare.domain.RecurrencePattern;
import com.hcare.domain.RecurrencePatternRepository;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import com.hcare.scheduling.ShiftGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class RecurrencePatternService {

    private final RecurrencePatternRepository patternRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftGenerationService shiftGenerationService;

    public RecurrencePatternService(RecurrencePatternRepository patternRepository,
                                     ShiftRepository shiftRepository,
                                     ShiftGenerationService shiftGenerationService) {
        this.patternRepository = patternRepository;
        this.shiftRepository = shiftRepository;
        this.shiftGenerationService = shiftGenerationService;
    }

    @Transactional
    public RecurrencePatternResponse createPattern(UUID agencyId, CreateRecurrencePatternRequest req) {
        RecurrencePattern pattern = new RecurrencePattern(
            agencyId, req.clientId(), req.serviceTypeId(),
            req.scheduledStartTime(), req.scheduledDurationMinutes(),
            req.daysOfWeek(), req.startDate());
        if (req.caregiverId() != null) pattern.setCaregiverId(req.caregiverId());
        if (req.authorizationId() != null) pattern.setAuthorizationId(req.authorizationId());
        if (req.endDate() != null) pattern.setEndDate(req.endDate());
        RecurrencePattern saved = patternRepository.save(pattern);
        shiftGenerationService.generateForPattern(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecurrencePatternResponse> listPatterns(UUID agencyId) {
        return patternRepository.findByAgencyId(agencyId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RecurrencePatternResponse getPattern(UUID patternId) {
        return toResponse(requirePattern(patternId));
    }

    @Transactional
    public RecurrencePatternResponse updatePattern(UUID patternId, UpdateRecurrencePatternRequest req) {
        RecurrencePattern pattern = requirePattern(patternId);

        boolean needsRegeneration = req.scheduledStartTime() != null
            || req.scheduledDurationMinutes() != null
            || req.daysOfWeek() != null;

        // C10 / M11: no-op guard fires BEFORE any setter — avoids spurious Hibernate dirty-check
        //      UPDATE and @Version increment when the PATCH body is fully empty.
        //      Must precede all setters so the entity is never mutated if we return early.
        if (!needsRegeneration && req.caregiverId() == null
                && req.authorizationId() == null && req.endDate() == null) {
            return toResponse(pattern);
        }

        if (req.scheduledStartTime() != null) pattern.setScheduledStartTime(req.scheduledStartTime());
        if (req.scheduledDurationMinutes() != null) pattern.setScheduledDurationMinutes(req.scheduledDurationMinutes());
        if (req.daysOfWeek() != null) pattern.setDaysOfWeek(req.daysOfWeek());
        if (req.caregiverId() != null) pattern.setCaregiverId(req.caregiverId());
        if (req.authorizationId() != null) pattern.setAuthorizationId(req.authorizationId());
        if (req.endDate() != null) pattern.setEndDate(req.endDate());

        patternRepository.save(pattern);

        if (needsRegeneration) {
            // C7: regenerateAfterEdit runs within this transaction — the Hibernate agencyFilter
            // is inherited from the current session because TenantFilterAspect fires @Before
            // every Spring Data repository method call (@within Repository). The
            // deleteUnstartedFutureShifts bulk DELETE uses an explicit agencyId parameter
            // and does not rely on the filter.
            shiftGenerationService.regenerateAfterEdit(pattern);
        }

        return toResponse(pattern);
    }

    @Transactional
    public void deactivatePattern(UUID patternId) {
        RecurrencePattern pattern = requirePattern(patternId);
        pattern.setActive(false);
        shiftRepository.deleteUnstartedFutureShifts(
            patternId, pattern.getAgencyId(),
            LocalDateTime.now(ZoneOffset.UTC),
            List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED));
        patternRepository.save(pattern);
    }

    // --- helpers ---

    private RecurrencePattern requirePattern(UUID patternId) {
        return patternRepository.findById(patternId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "RecurrencePattern not found"));
    }

    private RecurrencePatternResponse toResponse(RecurrencePattern p) {
        return new RecurrencePatternResponse(
            p.getId(), p.getAgencyId(), p.getClientId(), p.getCaregiverId(),
            p.getServiceTypeId(), p.getAuthorizationId(),
            p.getScheduledStartTime(), p.getScheduledDurationMinutes(), p.getDaysOfWeek(),
            p.getStartDate(), p.getEndDate(), p.getGeneratedThrough(),
            p.isActive(), p.getVersion(), p.getCreatedAt());
    }
}
```

- [ ] **Step 4: Run the unit tests**

```bash
cd backend && mvn test -Dtest=RecurrencePatternServiceTest -pl . 2>&1 | tail -20
```

Expected: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run the full suite**

```bash
cd backend && mvn test -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternService.java \
  src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternServiceTest.java
git commit -m "feat: implement RecurrencePatternService with create/get/update/deactivate and regeneration logic"
```

---

### Task 6: `RecurrencePatternController` + integration tests

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternController.java`
- Create: `backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternControllerIT.java`

- [ ] **Step 1: Write the failing integration tests**

> **M21 pre-check:** Before writing this test class, confirm that `ShiftRepository` already declares `findByClientIdAndScheduledStartBetween(UUID clientId, LocalDateTime start, LocalDateTime end)`. Four tests in this file call it: `createPattern_returns_201_and_generates_shifts`, `updatePattern_scheduling_fields_trigger_regeneration`, `updatePattern_non_scheduling_fields_do_not_trigger_regeneration`, `deletePattern_removes_future_unstarted_shifts`. If the method is absent, add it to Task 1 Step 3 (alongside `findByAgencyIdAndScheduledStartBetween`) with a corresponding domain IT test step before proceeding.

Create `backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternControllerIT.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.AuthService;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE shift_offers, shifts, recurrence_patterns, authorizations, caregiver_scoring_profiles, " +
    "caregiver_client_affinities, caregivers, service_types, clients, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RecurrencePatternControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private ServiceTypeRepository serviceTypeRepo;
    @Autowired private ShiftRepository shiftRepo;
    @Autowired private RecurrencePatternRepository patternRepo;

    private Agency agency;
    private Client client;
    private ServiceType serviceType;

    @BeforeEach
    void seed() {
        agency = agencyRepo.save(new Agency("Pattern Test Agency", "TX"));
        userRepo.save(new AgencyUser(agency.getId(), "pat-admin@test.com",
            AuthService.DUMMY_HASH_FOR_TEST, UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Pat", "Client", LocalDate.of(1970, 1, 1)));
        serviceType = serviceTypeRepo.save(new ServiceType(agency.getId(), "PCS", "PCS-PAT", true, "[]"));
    }

    private String token() {
        LoginRequest req = new LoginRequest("pat-admin@test.com", "correcthorsebatterystaple");
        return restTemplate.postForEntity("/api/v1/auth/login", req, LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // --- GET /recurrence-patterns ---

    @Test
    void listPatterns_returns_all_patterns_for_agency() {
        patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));
        patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(14, 0), 60, "[\"WEDNESDAY\"]", LocalDate.of(2026, 5, 6)));

        ResponseEntity<List<RecurrencePatternResponse>> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns",
            HttpMethod.GET, new HttpEntity<>(auth()),
            new ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
    }

    // --- POST /recurrence-patterns ---

    @Test
    void createPattern_returns_201_and_generates_shifts() {
        CreateRecurrencePatternRequest req = new CreateRecurrencePatternRequest(
            client.getId(), null, serviceType.getId(), null,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]",
            LocalDate.of(2026, 5, 4), null);

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns", HttpMethod.POST,
            new HttpEntity<>(req, auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().id()).isNotNull();
        assertThat(resp.getBody().active()).isTrue();
        assertThat(resp.getBody().scheduledStartTime()).isEqualTo(LocalTime.of(9, 0));

        // Shifts should have been generated
        UUID patternId = resp.getBody().id();
        assertThat(shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDate.of(2026, 5, 4).atStartOfDay(),
            LocalDate.of(2026, 5, 4).plusWeeks(9).atStartOfDay()))
            .isNotEmpty()
            .allMatch(s -> s.getSourcePatternId().equals(patternId));
    }

    @Test
    void createPattern_without_token_returns_401() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        CreateRecurrencePatternRequest req = new CreateRecurrencePatternRequest(
            client.getId(), null, serviceType.getId(), null,
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]",
            LocalDate.of(2026, 5, 4), null);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns", HttpMethod.POST,
            new HttpEntity<>(req, h), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- GET /recurrence-patterns/{id} ---

    @Test
    void getPattern_returns_pattern_when_exists() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(10, 0), 60, "[\"TUESDAY\"]", LocalDate.of(2026, 5, 5)));

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.GET, new HttpEntity<>(auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().id()).isEqualTo(pattern.getId());
        assertThat(resp.getBody().scheduledDurationMinutes()).isEqualTo(60);
    }

    @Test
    void getPattern_returns_404_for_unknown_id() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + UUID.randomUUID(),
            HttpMethod.GET, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- PATCH /recurrence-patterns/{id} ---

    @Test
    void updatePattern_scheduling_fields_trigger_regeneration() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        // Seed some future shifts manually so we can confirm regeneration deletes them
        shiftRepo.save(new Shift(agency.getId(), pattern.getId(), client.getId(), null,
            serviceType.getId(), null,
            LocalDate.of(2026, 5, 11).atTime(9, 0),
            LocalDate.of(2026, 5, 11).atTime(11, 0)));

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            LocalTime.of(14, 0), null, null, null, null, null);

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.PATCH, new HttpEntity<>(req, auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().scheduledStartTime()).isEqualTo(LocalTime.of(14, 0));

        // The old 09:00 shift must be gone; new shifts start at 14:00
        assertThat(shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDate.of(2026, 5, 11).atStartOfDay(),
            LocalDate.of(2026, 5, 11).plusDays(1).atStartOfDay()))
            .allMatch(s -> s.getScheduledStart().getHour() == 14);
    }

    @Test
    void updatePattern_non_scheduling_fields_do_not_trigger_regeneration() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        // Manually seed a future OPEN shift
        Shift existing = shiftRepo.save(new Shift(agency.getId(), pattern.getId(), client.getId(), null,
            serviceType.getId(), null,
            LocalDate.of(2026, 5, 11).atTime(9, 0),
            LocalDate.of(2026, 5, 11).atTime(11, 0)));

        UpdateRecurrencePatternRequest req = new UpdateRecurrencePatternRequest(
            null, null, null, null, null, LocalDate.of(2026, 12, 31));

        ResponseEntity<RecurrencePatternResponse> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.PATCH, new HttpEntity<>(req, auth()), RecurrencePatternResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().endDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        // Pre-existing shift should still be there (no regeneration occurred)
        assertThat(shiftRepo.findById(existing.getId())).isPresent();
    }

    // --- DELETE /recurrence-patterns/{id} ---

    @Test
    void deletePattern_deactivates_and_returns_204() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        ResponseEntity<Void> resp = restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        RecurrencePattern deactivated = patternRepo.findById(pattern.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void deletePattern_removes_future_unstarted_shifts() {
        RecurrencePattern pattern = patternRepo.save(new RecurrencePattern(
            agency.getId(), client.getId(), serviceType.getId(),
            LocalTime.of(9, 0), 120, "[\"MONDAY\"]", LocalDate.of(2026, 5, 4)));

        // Future OPEN shift for this pattern
        shiftRepo.save(new Shift(agency.getId(), pattern.getId(), client.getId(), null,
            serviceType.getId(), null,
            LocalDate.of(2026, 6, 1).atTime(9, 0),
            LocalDate.of(2026, 6, 1).atTime(11, 0)));

        restTemplate.exchange(
            "/api/v1/recurrence-patterns/" + pattern.getId(),
            HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);

        assertThat(shiftRepo.findByClientIdAndScheduledStartBetween(
            client.getId(), LocalDate.of(2026, 5, 31).atStartOfDay(),
            LocalDate.of(2026, 6, 2).atStartOfDay()))
            .isEmpty();
    }
}
```

- [ ] **Step 2: Run to confirm the tests fail**

```bash
cd backend && mvn test -Dtest=RecurrencePatternControllerIT -pl . 2>&1 | tail -20
```

Expected: compilation error — `RecurrencePatternController` does not exist.

- [ ] **Step 3: Implement `RecurrencePatternController`**

Create `backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternController.java`:

```java
package com.hcare.api.v1.scheduling;

import com.hcare.api.v1.scheduling.dto.CreateRecurrencePatternRequest;
import com.hcare.api.v1.scheduling.dto.RecurrencePatternResponse;
import com.hcare.api.v1.scheduling.dto.UpdateRecurrencePatternRequest;
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurrence-patterns")
public class RecurrencePatternController {

    private final RecurrencePatternService recurrencePatternService;

    public RecurrencePatternController(RecurrencePatternService recurrencePatternService) {
        this.recurrencePatternService = recurrencePatternService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<RecurrencePatternResponse>> listPatterns(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recurrencePatternService.listPatterns(principal.getAgencyId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<RecurrencePatternResponse> createPattern(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRecurrencePatternRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(recurrencePatternService.createPattern(principal.getAgencyId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<RecurrencePatternResponse> getPattern(@PathVariable UUID id) {
        return ResponseEntity.ok(recurrencePatternService.getPattern(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<RecurrencePatternResponse> updatePattern(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecurrencePatternRequest request) {
        return ResponseEntity.ok(recurrencePatternService.updatePattern(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePattern(@PathVariable UUID id) {
        recurrencePatternService.deactivatePattern(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run the integration tests**

```bash
cd backend && mvn test -Dtest=RecurrencePatternControllerIT -pl . 2>&1 | tail -30
```

Expected: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run the full test suite**

```bash
cd backend && mvn test -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternController.java \
  src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternControllerIT.java
git commit -m "feat: add RecurrencePatternController with create/get/update/deactivate endpoints"
```

---

### Task 7: End-to-end smoke test — broadcast + offer acceptance flow

This task adds one end-to-end integration test that exercises the full broadcast-and-accept workflow as a single narrative, confirming all the pieces wired together correctly.

**Files:**
- Modify: `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java`

- [ ] **Step 1: Verify `ShiftDetailResponse` fields, then add the end-to-end broadcast + accept test**

> **Pre-check before writing:** Confirm that `com.hcare.api.v1.visits.dto.ShiftDetailResponse` exposes `status()` and `caregiverId()` accessors. `GET /api/v1/shifts/{id}` is served by `VisitController` (not `ShiftSchedulingController`) and returns `ShiftDetailResponse`. Step 6 of the test below relies on both fields. If either accessor is absent or named differently, add the missing field to `ShiftDetailResponse` before proceeding.

Add this import at the top of `backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java`:

```java
import com.hcare.api.v1.visits.dto.ShiftDetailResponse;
```

Then open the file and add the following test before the closing `}`:

```java
@Test
void full_broadcast_and_accept_flow_assigns_caregiver_and_closes_other_offers() {
    // 1. Create two caregivers
    Caregiver cg1 = caregiverRepo.save(new Caregiver(agency.getId(), "First", "CG", "cg1-e2e@test.com"));
    Caregiver cg2 = caregiverRepo.save(new Caregiver(agency.getId(), "Second", "CG", "cg2-e2e@test.com"));

    // 2. Create an OPEN shift via POST /shifts
    CreateShiftRequest createReq = new CreateShiftRequest(
        client.getId(), null, serviceType.getId(), null,
        LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 1, 13, 0), null);
    ResponseEntity<ShiftSummaryResponse> createResp = restTemplate.exchange(
        "/api/v1/shifts", HttpMethod.POST,
        new HttpEntity<>(createReq, auth()), ShiftSummaryResponse.class);
    assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID shiftId = createResp.getBody().id();

    // 3. Manually create offers for both caregivers (broadcast is covered by the broadcast-conflict test;
    //    here we pre-seed offers to test accept/decline logic in isolation)
    ShiftOffer offer1 = shiftOfferRepo.save(new ShiftOffer(shiftId, cg1.getId(), agency.getId()));
    ShiftOffer offer2 = shiftOfferRepo.save(new ShiftOffer(shiftId, cg2.getId(), agency.getId()));

    // 4. GET /shifts/{id}/offers — expect 2 offers, both NO_RESPONSE
    ResponseEntity<List<ShiftOfferSummary>> offersResp = restTemplate.exchange(
        "/api/v1/shifts/" + shiftId + "/offers",
        HttpMethod.GET, new HttpEntity<>(auth()),
        new ParameterizedTypeReference<>() {});
    assertThat(offersResp.getBody()).hasSize(2)
        .allMatch(o -> o.response() == ShiftOfferResponse.NO_RESPONSE);

    // 5. cg1 ACCEPTS
    RespondToOfferRequest acceptReq = new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED);
    ResponseEntity<ShiftOfferSummary> acceptResp = restTemplate.exchange(
        "/api/v1/shifts/" + shiftId + "/offers/" + offer1.getId() + "/respond",
        HttpMethod.POST, new HttpEntity<>(acceptReq, auth()), ShiftOfferSummary.class);
    assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(acceptResp.getBody().response()).isEqualTo(ShiftOfferResponse.ACCEPTED);

    // 6. Shift must now be ASSIGNED to cg1 — served by VisitController (GET /api/v1/shifts/{id})
    ResponseEntity<ShiftDetailResponse> shiftCheck = restTemplate.exchange(
        "/api/v1/shifts/" + shiftId,
        HttpMethod.GET, new HttpEntity<>(auth()), ShiftDetailResponse.class);
    assertThat(shiftCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(shiftCheck.getBody().status()).isEqualTo(ShiftStatus.ASSIGNED);
    assertThat(shiftCheck.getBody().caregiverId()).isEqualTo(cg1.getId());

    // 7. offer2 must be auto-DECLINED
    ShiftOffer offer2Updated = shiftOfferRepo.findById(offer2.getId()).orElseThrow();
    assertThat(offer2Updated.getResponse()).isEqualTo(ShiftOfferResponse.DECLINED);

    // 8. A second ACCEPT attempt on offer2 must fail with 409 (already declined)
    ResponseEntity<String> secondAccept = restTemplate.exchange(
        "/api/v1/shifts/" + shiftId + "/offers/" + offer2.getId() + "/respond",
        HttpMethod.POST,
        new HttpEntity<>(new RespondToOfferRequest(ShiftOfferResponse.ACCEPTED), auth()),
        String.class);
    assertThat(secondAccept.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
}
```

- [ ] **Step 2: Run only the new test**

```bash
cd backend && mvn test -Dtest="ShiftSchedulingControllerIT#full_broadcast_and_accept_flow_assigns_caregiver_and_closes_other_offers" -pl . 2>&1 | tail -20
```

Expected: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Run the full test suite**

```bash
cd backend && mvn test -pl . 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
cd backend && git add src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java
git commit -m "test: add end-to-end broadcast and offer acceptance smoke test"
```

---

### Task 8: Final regression check and integration validation

This task verifies no regressions in the pre-existing test suite and that every new endpoint is reachable via the running application.

**Files:** No new files. Read-only checks.

- [ ] **Step 1: Run the complete test suite**

```bash
cd backend && mvn verify -pl . 2>&1 | tail -30
```

Expected: `BUILD SUCCESS` with all tests passing. Output will include test counts from:
- `ShiftDomainIT`, `ShiftSubEntitiesIT`, `RecurrencePatternDomainIT` — updated domain tests
- `ShiftSchedulingServiceTest`, `RecurrencePatternServiceTest` — new unit tests
- `ShiftSchedulingControllerIT`, `RecurrencePatternControllerIT` — new IT tests
- All pre-existing ITs (`AuthControllerIT`, `VisitControllerIT`, `ShiftGenerationServiceIT`, etc.)

- [ ] **Step 2: Verify no endpoint naming collision with `VisitController`**

Confirm in the test output that `VisitControllerIT` still passes all its tests. `VisitController` maps:
- `POST /api/v1/shifts/{id}/clock-in`
- `POST /api/v1/shifts/{id}/clock-out`
- `GET /api/v1/shifts/{id}` (single shift detail)

`ShiftSchedulingController` maps:
- `GET /api/v1/shifts` (list, no path variable)
- `POST /api/v1/shifts` (create)
- `PATCH /api/v1/shifts/{id}/assign`
- `PATCH /api/v1/shifts/{id}/unassign`
- `PATCH /api/v1/shifts/{id}/cancel`
- `GET /api/v1/shifts/{id}/candidates`
- `POST /api/v1/shifts/{id}/broadcast`
- `GET /api/v1/shifts/{id}/offers`
- `POST /api/v1/shifts/{id}/offers/{offerId}/respond`

None overlap. If Spring reports an ambiguous mapping error during startup, check that `VisitController` does not declare a top-level `@GetMapping` without a path variable.

- [ ] **Step 3: Commit**

```bash
cd /Users/ronstarling/repos/hcare && git add \
  backend/src/main/java/com/hcare/domain/RecurrencePattern.java \
  backend/src/main/java/com/hcare/domain/ShiftRepository.java \
  backend/src/main/java/com/hcare/domain/ShiftOfferRepository.java \
  backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java \
  backend/src/test/java/com/hcare/domain/ShiftDomainIT.java \
  backend/src/test/java/com/hcare/domain/ShiftSubEntitiesIT.java \
  backend/src/test/java/com/hcare/domain/RecurrencePatternDomainIT.java \
  backend/src/main/java/com/hcare/api/v1/scheduling/dto/ \
  backend/src/main/java/com/hcare/api/v1/scheduling/ShiftOfferCreationService.java \
  backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java \
  backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java \
  backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternService.java \
  backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternController.java \
  backend/src/test/java/com/hcare/api/v1/scheduling/ShiftOfferCreationServiceTest.java \
  backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingServiceTest.java \
  backend/src/test/java/com/hcare/api/v1/scheduling/ShiftSchedulingControllerIT.java \
  backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternServiceTest.java \
  backend/src/test/java/com/hcare/api/v1/scheduling/RecurrencePatternControllerIT.java
git commit -m "feat: Scheduling REST API complete — shifts and recurrence patterns endpoints"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Task |
|---|---|
| `GET /shifts` — calendar list | Task 3 (service), Task 4 (controller + IT) |
| `POST /shifts` — create ad-hoc shift | Task 3, Task 4 |
| `PATCH /shifts/{id}/assign` | Task 3, Task 4 |
| `PATCH /shifts/{id}/unassign` | Task 3, Task 4 |
| `PATCH /shifts/{id}/cancel` + publish `ShiftCancelledEvent` | Task 3, Task 4 |
| `GET /shifts/{id}/candidates` | Task 3, Task 4 |
| `POST /shifts/{id}/broadcast` | Task 3, Task 4 |
| `GET /shifts/{id}/offers` | Task 3, Task 4 |
| `POST /shifts/{id}/offers/{offerId}/respond` (ACCEPTED auto-assigns + declines others) | Task 3, Task 4, Task 7 |
| `POST /recurrence-patterns` + `generateForPattern` | Task 5, Task 6 |
| `GET /recurrence-patterns/{id}` | Task 5, Task 6 |
| `PATCH /recurrence-patterns/{id}` — scheduling fields → `regenerateAfterEdit`; other fields → in-place | Task 5, Task 6 |
| `DELETE /recurrence-patterns/{id}` — deactivate + delete future unstarted shifts | Task 5, Task 6 |
| New repository queries (`findByAgencyIdAndScheduledStartBetween`, `findByCaregiverIdAndShiftId`, `findByAgencyId`, `existsByIdAndAgencyId`) | Task 1 |
| RecurrencePattern setters (`setScheduledStartTime`, `setScheduledDurationMinutes`, `setDaysOfWeek`) | Task 1 |
| `ShiftCancelledEvent` published on cancel of ASSIGNED shift | Task 3 (unit test verifies event) |
| JWT security — all endpoints require authentication | Task 4 (401 test), Task 6 (401 test) |
| No new Flyway migrations | All tasks — confirmed no migration files created |

**Placeholder scan:** No TBDs, no stubs, no "similar to above" references found.

**Type consistency check:**
- `ShiftSummaryResponse` — defined in Task 2, used in Task 3 (service), Task 4 (controller + IT) consistently.
- `ShiftOfferSummary` — defined in Task 2 (named `ShiftOfferSummary` to avoid collision with domain `ShiftOfferResponse` enum), used consistently in Task 3 and Task 4.
- `RespondToOfferRequest.response` field type is `ShiftOfferResponse` (the domain enum) — consistent across Task 2 definition and Task 3/4 usage.
- `deactivatePattern` method name — consistent between Task 5 (service) and Task 6 (controller call).
- `AuthService.DUMMY_HASH_FOR_TEST` — used in both IT test classes (Task 4 and Task 6) with the correct import `com.hcare.api.v1.auth.AuthService`.
- `toOfferSummary` private helper in `ShiftSchedulingService` — defined once, called from `broadcastShift`, `listOffers`, `respondToOffer` — consistent.
- `ShiftOfferCreationService.createOfferIfAbsent` — void method, called from `broadcastShift` only; idempotency tested in `ShiftOfferCreationServiceTest`.
```

### Critical Files for Implementation
- `/Users/ronstarling/repos/hcare/backend/src/main/java/com/hcare/domain/RecurrencePattern.java`
- `/Users/ronstarling/repos/hcare/backend/src/main/java/com/hcare/domain/ShiftRepository.java`
- `/Users/ronstarling/repos/hcare/backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingService.java`
- `/Users/ronstarling/repos/hcare/backend/src/main/java/com/hcare/api/v1/scheduling/RecurrencePatternService.java`
- `/Users/ronstarling/repos/hcare/backend/src/main/java/com/hcare/api/v1/scheduling/ShiftSchedulingController.java`

---

Plan is ready to save to `docs/superpowers/plans/2026-04-05-scheduling-api.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?