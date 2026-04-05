# Critical Implementation Review — Shift & Recurrence Domain (Plan 3)
**Review #:** 1  
**Plan reviewed:** `docs/superpowers/plans/2026-04-05-shift-recurrence-domain.md`  
**Previous reviews:** none

---

## 1. Overall Assessment

The plan is well-structured, complete, and follows established codebase patterns consistently. TDD discipline is solid across all 8 tasks, multi-tenancy treatment is correct (explicit `agencyId` on the bulk DELETE, `TransactionTemplate` wrapper for filter tests, documented scheduler intent), and the EVV raw-data-only contract is correctly upheld. One critical operational issue was found in the nightly scheduler and one significant correctness risk was found in the repository layer. Several minor issues round out the review — none are blockers individually, but the critical items should be addressed before implementation.

---

## 2. Critical Issues

### C1 — `advanceGenerationFrontier` has no per-pattern exception isolation

**File:** `backend/src/main/java/com/hcare/scheduling/ShiftGenerationScheduler.java`

**Problem:** The nightly job iterates all active patterns and calls `generateForPattern` for each. If any single pattern throws — including `ObjectOptimisticLockingFailureException` (which is likely when a scheduler run overlaps with a concurrent save) — the exception propagates uncaught and the entire `advanceGenerationFrontier()` call aborts. Every pattern after the failing one is skipped silently.

**Why it matters:** The `RecurrencePattern` entity carries `@Version` specifically because concurrent saves can race with the nightly job. The plan acknowledges this in the architecture section. Yet the scheduler has no protection against it — the same mechanism that motivates `@Version` will cause partial nightly runs in production.

**Fix:**

```java
@Scheduled(cron = "${hcare.scheduling.shift-generation-cron:0 0 2 * * *}")
public void advanceGenerationFrontier() {
    LocalDate horizon = LocalDate.now().plusWeeks(LocalShiftGenerationService.HORIZON_WEEKS);
    List<RecurrencePattern> patterns = patternRepository.findActivePatternsBehindHorizon(horizon);
    for (RecurrencePattern pattern : patterns) {
        try {
            shiftGenerationService.generateForPattern(pattern);
        } catch (Exception e) {
            // Log and continue — one failed pattern must not block all others.
            // ObjectOptimisticLockingFailureException is the expected concurrent-edit case;
            // the pattern will be retried on the next nightly run.
            log.error("Failed to generate shifts for pattern {} (agency {}): {}",
                pattern.getId(), pattern.getAgencyId(), e.getMessage());
        }
    }
}
```

Add `private static final Logger log = LoggerFactory.getLogger(ShiftGenerationScheduler.class);` and import `org.slf4j.Logger` / `org.slf4j.LoggerFactory`.

The IT test `scheduler_advanceGenerationFrontier_generates_shifts_for_patterns_behind_horizon` does not need changes — it invokes the method directly without triggering optimistic lock conflicts.

---

### C2 — `deleteUnstartedFutureShifts` missing `clearAutomatically = true` — stale first-level cache risk

**File:** `backend/src/main/java/com/hcare/domain/ShiftRepository.java`

**Problem:** The `@Modifying` JPQL DELETE is a bulk DML statement that bypasses Hibernate's first-level (session-level) cache. Any `Shift` entities that were loaded into the session before the DELETE still appear as managed entities in the persistence context after the DELETE executes. If Hibernate flushes the session again later in the same transaction it can attempt to re-persist the deleted entities.

In the current `regenerateAfterEdit` call sequence (delete → reset generatedThrough → `generateForPattern` which calls `saveAll` and `patternRepository.save`), there is no re-read of the deleted shifts, so this does not cause a visible bug today. However, `clearAutomatically = true` is the correct defensive pattern for any `@Modifying` bulk DML and costs nothing.

**Fix:**

```java
@Modifying(clearAutomatically = true)
@Query("DELETE FROM Shift s WHERE s.sourcePatternId = :patternId " +
       "AND s.agencyId = :agencyId " +
       "AND s.scheduledStart > :cutoff " +
       "AND s.status IN :statuses")
void deleteUnstartedFutureShifts(@Param("patternId") UUID patternId,
                                  @Param("agencyId") UUID agencyId,
                                  @Param("cutoff") LocalDateTime cutoff,
                                  @Param("statuses") Collection<ShiftStatus> statuses);
```

---

## 3. Previously Addressed Items

No prior reviews exist for this plan.

---

## 4. Minor Issues & Improvements

### M1 — `boolean isActive` field naming is non-idiomatic and inconsistent with its own setter

**File:** `backend/src/main/java/com/hcare/domain/RecurrencePattern.java`

A Java boolean field named `isActive` is unusual — the `is` prefix belongs on the **getter**, not the field. The result is that the field is `isActive` but the setter is `setActive(boolean active)` — these do not correspond to the same property by Java bean conventions. Frameworks that use getter-based property discovery (MapStruct, some serializers) would infer a field named `active` from the getter `isActive()` and setter `setActive()`, while the actual field is `isActive`. This can cause silent mapping failures in downstream Plans when DTOs or events are built from the entity.

Field-based JPA access (correctly used here) avoids the Hibernate JPQL issue, but the naming inconsistency is still a latent bug for anything that does property introspection.

**Fix:** Rename the field from `isActive` to `active` everywhere in the entity:

```java
@Column(name = "is_active", nullable = false)
private boolean active = true;

public void setActive(boolean active) { this.active = active; }
public boolean isActive() { return active; }
```

Update the JPQL query to match:

```java
@Query("SELECT rp FROM RecurrencePattern rp WHERE rp.active = true AND rp.generatedThrough < :horizon")
List<RecurrencePattern> findActivePatternsBehindHorizon(@Param("horizon") LocalDate horizon);
```

All callers already use `isActive()` / `setActive()` — no other changes required.

---

### M2 — No direct test for `deleteUnstartedFutureShifts` boundary conditions

**Task 3** provides no test that directly exercises `deleteUnstartedFutureShifts`. The method is only invoked indirectly through `regenerateAfterEdit` in Tasks 7–8. The three critical correctness properties of the bulk delete — (a) `agencyId` isolation does not delete a different agency's shifts, (b) IN_PROGRESS/COMPLETED shifts are not deleted, (c) shifts before the cutoff are preserved — have no coverage.

Add a test to `ShiftDomainIT`:

```java
@Test
void deleteUnstartedFutureShifts_leaves_completed_and_other_agency_shifts_intact() {
    Agency agencyA = agencyRepo.save(new Agency("Delete Agency A", "TX"));
    Agency agencyB = agencyRepo.save(new Agency("Delete Agency B", "CA"));
    Client clientA = clientRepo.save(new Client(agencyA.getId(), "Del", "A", LocalDate.of(1960,1,1)));
    Client clientB = clientRepo.save(new Client(agencyB.getId(), "Del", "B", LocalDate.of(1960,1,1)));
    ServiceType stA = serviceTypeRepo.save(new ServiceType(agencyA.getId(), "PCS", "PCS-DEL-A", true, "[]"));
    ServiceType stB = serviceTypeRepo.save(new ServiceType(agencyB.getId(), "PCS", "PCS-DEL-B", true, "[]"));

    UUID patternId = UUID.randomUUID();
    LocalDateTime future = LocalDateTime.now().plusDays(7);

    // Should be deleted: agencyA OPEN shift in future
    Shift toDelete = shiftRepo.save(new Shift(agencyA.getId(), patternId, clientA.getId(), null,
        stA.getId(), null, future, future.plusHours(4)));

    // Should survive: COMPLETED shift for same pattern
    Shift completed = shiftRepo.save(new Shift(agencyA.getId(), patternId, clientA.getId(), null,
        stA.getId(), null, future.plusDays(1), future.plusDays(1).plusHours(4)));
    completed.setStatus(ShiftStatus.COMPLETED);
    shiftRepo.save(completed);

    // Should survive: agencyB's OPEN shift for same patternId (different agency)
    Shift otherAgency = shiftRepo.save(new Shift(agencyB.getId(), patternId, clientB.getId(), null,
        stB.getId(), null, future, future.plusHours(4)));

    shiftRepo.deleteUnstartedFutureShifts(
        patternId, agencyA.getId(), LocalDateTime.now(),
        List.of(ShiftStatus.OPEN, ShiftStatus.ASSIGNED)
    );
    shiftRepo.flush();

    assertThat(shiftRepo.findById(toDelete.getId())).isEmpty();
    assertThat(shiftRepo.findById(completed.getId())).isPresent();
    assertThat(shiftRepo.findById(otherAgency.getId())).isPresent();
}
```

---

### M3 — Nightly scheduler queries patterns whose `endDate` is in the past (repeated no-ops)

**File:** `backend/src/main/java/com/hcare/domain/RecurrencePatternRepository.java`

`findActivePatternsBehindHorizon` has no `endDate` filter. A pattern with `endDate = 2024-01-01` and `is_active = true` is returned on every nightly run. `generateForPattern` correctly no-ops it (`start.isAfter(end)` is immediately true), but the pattern is loaded, deserialized, and passed to the service unnecessarily on every run forever.

For the MVP scale (1–25 caregivers) this is harmless. When introducing the query to Plan 4 or 5, add the filter to avoid confusion:

```java
@Query("""
    SELECT rp FROM RecurrencePattern rp
    WHERE rp.active = true
      AND rp.generatedThrough < :horizon
      AND (rp.endDate IS NULL OR rp.endDate >= :today)
    """)
List<RecurrencePattern> findActivePatternsBehindHorizon(
    @Param("horizon") LocalDate horizon,
    @Param("today") LocalDate today);
```

Update callers: `patternRepository.findActivePatternsBehindHorizon(horizon, LocalDate.now())`.

---

### M4 — Missing composite index for the nightly scheduler query

**File:** `backend/src/main/resources/db/migration/V6__shift_domain_schema.sql`

The scheduler query filters `is_active = true AND generated_through < :horizon`. There is no index covering both columns. With the current single-agency MVP scale this is fine, but the scheduler runs daily and scans the full table every time.

Add to the migration after the existing `recurrence_patterns` indexes:

```sql
CREATE INDEX idx_recurrence_patterns_active_frontier
    ON recurrence_patterns(is_active, generated_through)
    WHERE is_active = TRUE;
```

The partial index (`WHERE is_active = TRUE`) covers the common case (most patterns are active) and is much smaller than a full index.

---

### M5 — `CommunicationMessage.sentAt` and `createdAt` are redundant

**File:** `backend/src/main/java/com/hcare/domain/CommunicationMessage.java`

Both fields are set to `LocalDateTime.now()` at construction and neither is set by the database (`DEFAULT CURRENT_TIMESTAMP` in SQL is unused because JPA inserts the Java-side value). They will always be equal (within nanoseconds). The semantic distinction between "when the user sent the message" and "when the row was created" is useful, but here `sentAt` is not set by the caller — it's set automatically, the same as `createdAt`.

Either: (a) drop `createdAt` and keep only `sentAt` (since messages are immutable, insertion time = send time), or (b) make `sentAt` a caller-supplied parameter rather than an auto-set field, so it can differ from the server insertion time. Option (a) is simpler:

```sql
-- V6: keep only sent_at in communication_messages, drop created_at column
```

```java
// Remove createdAt field and its getter from CommunicationMessage.java
```

---

## 5. Questions for Clarification

1. **Pattern deactivation flow**: When a `RecurrencePattern` is deactivated (`setActive(false)`), should `regenerateAfterEdit` still delete future OPEN/ASSIGNED shifts? The current plan doesn't expose a `deactivate()` helper on the service — this will be left to Plan 4/5 callers. Worth a note in the interface Javadoc.

2. **`EvvRecord.clientMedicaidId` denormalization**: This is stored on `EvvRecord` even though `Client.medicaidId` already exists. The plan's EVV comment explains this as a snapshot — but the field is never populated by `LocalShiftGenerationService` or any code in Plan 3. Plan 4 will presumably set it at clock-in time. Worth confirming this is intentional (not a gap) before Plan 4 starts.

---

## 6. Final Recommendation

**Approve with changes.**

Fix C1 (scheduler exception isolation) and C2 (`clearAutomatically`) before implementation begins — both are small, contained changes. M2 (bulk-delete test) should also be added to Task 3 since it tests a correctness property that has no other coverage. M1 (field renaming) is a clean-up that is cheap to do now and expensive to do after Plan 4 builds on `isActive()`. M3–M5 are optional improvements.
