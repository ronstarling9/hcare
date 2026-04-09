# Critical Implementation Review — Plan 2: Family Portal Backend Endpoints
**Review #3** | Reviewer: Claude Sonnet 4.6 | Date: 2026-04-08

---

## 1. Overall Assessment

This is the third review pass. Both critical issues from Review 2 — the residual N+1 in `buildTodayVisitDto` (Issue A) and the non-exhaustive `mapShiftStatus` switch (Issue B) — have been fully resolved. The bulk-fetch maps now cover `todayShift` and `upcomingShifts` uniformly, and `MISSED` is an explicit case with no `default` branch. The plan is substantively stronger across its three review iterations. However, four items from Review 2's minor section remain unresolved and two of them are closer to correctness blockers than style notes: the missing `deleteExpired` method declaration in `FamilyPortalTokenRepository` (which will produce a startup/runtime failure) and the cross-agency isolation test that is described in prose but absent from the actual test file. Additionally, two new issues have been identified that were not visible in prior reviews.

---

## 2. Critical Issues

### Issue C — `TenantContext.get()` in `ClientController.inviteFamilyPortalUser` can silently pass null `agencyId`

**What:** The invite endpoint reads the agency identifier from the ThreadLocal directly:
```java
UUID agencyId = com.hcare.multitenancy.TenantContext.get();
return ResponseEntity.ok(familyPortalService.generateInvite(id, agencyId, request));
```
If `TenantContext` has not been populated for any reason (race during filter ordering, a missing filter registration, or a test that bypasses the interceptor), `get()` returns null. `generateInvite` then passes `null` as `agencyId` to `fpuRepo.findByClientIdAndAgencyIdAndEmail` and to `new FamilyPortalToken(tokenHash, fpu.getId(), clientId, null, expiresAt)`, creating a token row with a null `agencyId`. The `agencyFilter` predicate will never match this row for real tenants, causing the verify endpoint to silently succeed (the token hash lookup does not filter by `agencyId`) but the issued JWT will carry `null` as the `agencyId` claim — breaking every subsequent dashboard call.

**Why it matters:** This is a cross-tenant integrity and data-corruption risk. A null `agencyId` in a JWT claim will cause `getDashboard` to call `agencyRepo.findById(null)`, which either throws or returns empty and bubbles up as 500. More subtly, `TenantContext.get()` is the right place for the *filter* to enforce isolation, but controllers should extract `agencyId` from the authenticated `UserPrincipal` — a trusted, already-validated source. The CLAUDE.md convention is that `UserPrincipal` carries `agencyId`; `authStore` and the JWT claims are the canonical source.

**Fix:** Inject `@AuthenticationPrincipal UserPrincipal principal` into `inviteFamilyPortalUser` and extract `agencyId` from `principal.getAgencyId()`, exactly as `FamilyPortalDashboardController.getDashboard` does. Remove the direct `TenantContext.get()` call from the controller. This is also consistent with how every other controller method in the project obtains the current agency ID:
```java
@PostMapping("/{id}/family-portal-users/invite")
@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
public ResponseEntity<InviteResponse> inviteFamilyPortalUser(
        @PathVariable UUID id,
        @Valid @RequestBody InviteRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    clientService.requireClientForInvite(id);
    return ResponseEntity.ok(
        familyPortalService.generateInvite(id, principal.getAgencyId(), request));
}
```

---

### Issue D — `deleteExpired` is called but never declared in `FamilyPortalTokenRepository` (startup failure)

**What:** `FamilyPortalTokenCleanupJob.deleteExpiredTokens()` calls `tokenRepo.deleteExpired(LocalDateTime.now(ZoneOffset.UTC))`. `FamilyPortalTokenRepository` is defined in Plan 1 and this plan adds no step to declare `deleteExpired` there. `deleteExpired` is not a Spring Data JPA naming convention that can be auto-derived — it requires an explicit `@Modifying @Query` annotation. Without it, Spring Data will throw `No property 'deleteExpired' found for type FamilyPortalToken` at application startup.

**Why it matters:** This is a hard compile/startup blocker. Every test that starts the full application context (`@SpringBootTest`) will fail with a `BeanCreationException` before any test method runs.

**Fix:** Task 12 must include an explicit step to modify `FamilyPortalTokenRepository` (in `com.hcare.domain`):
```java
// Add to FamilyPortalTokenRepository:
@Modifying
@Query("DELETE FROM FamilyPortalToken t WHERE t.expiresAt < :now")
void deleteExpired(@Param("now") LocalDateTime now);
```
Add `backend/src/main/java/com/hcare/domain/FamilyPortalTokenRepository.java` to the `git add` in Task 12 Step 3.

---

## 3. Previously Addressed Items

The following issues from the full review history are resolved in this revision:

- **Review 1 Issue 1 (N+1 for upcoming visits)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 2 (missing `permitAll` on verify endpoint)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 3 (rate limiter X-Forwarded-For spoofing)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 4 (missing `findByClientIdAndAgencyIdAndEmail`)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 5 (90-day in-memory window)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 6 (fragile token extraction in tests)** — resolved in Review 2; confirmed intact.
- **Review 2 Issue A (residual N+1 in `buildTodayVisitDto`)** — now fixed: `getDashboard` collects all `caregiverId`/`serviceTypeId` values from both `todayShift` and `upcomingShifts` into a single bulk fetch; `buildCaregiverDto` is a pure formatting helper with no repository calls.
- **Review 2 Issue B (`mapShiftStatus` exhaustiveness / `MISSED` arm)** — now fixed: `MISSED` is an explicit case, no `default` branch, compiler will enforce exhaustiveness going forward.
- **Review 2 minor 4a (dead code block in `inviteThenVerify_happyPath_returnsPortalJwt`)** — now fixed: the stale `HttpEntity<String> inviteReq` construction (lines 993–995 in the previous draft) has been removed; `restTemplate.exchange` now uses the inline `new HttpEntity<>` directly.

---

## 4. Minor Issues & Improvements

**4a — `.toString()` on `LocalDateTime` timestamps still produces zone-less strings (Review 1 item 4a, Review 2 item 4c: unresolved)**

`buildTodayVisitDto` sets `clockedInAt` and `clockedOutAt` via `evv.get().getTimeIn().toString()` and `.getTimeOut().toString()`. `buildTodayVisitDto` also emits `shift.getScheduledStart().toString()` and `shift.getScheduledEnd().toString()`. `buildUpcomingDto` and `buildLastVisitDto` do the same. `LocalDateTime.toString()` omits trailing seconds/nanos and carries no zone designator (e.g., `2026-04-08T09:00` instead of `2026-04-08T09:00:00Z`). The DTO contract says "UTC ISO-8601." `expiresAtStr` in `generateInvite` correctly uses `.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)`. All other timestamp fields should follow the same pattern. This was raised in both Review 1 (item 4a) and Review 2 (item 4c) and remains unfixed.

**4b — `findTop3` and `findFirstByClientId...` still lack `agencyId` predicates (Review 2 item 4d: unresolved)**

`findTop3ByClientIdAndStatusNotInAndScheduledStartAfterOrderByScheduledStartAsc` uses only `clientId`. `findFirstByClientIdAndStatusOrderByScheduledStartDesc` (last-visit query) also uses only `clientId`. The plan's own rationale for adding an explicit `agencyId` predicate to the today-window query was that the Hibernate `agencyFilter` may not activate for `FAMILY_PORTAL`-role requests. That reasoning applies equally to these two derived queries. For belt-and-suspenders correctness, rename both to include `AgencyId` and add `agencyId` as a parameter, passing `agencyId` from the `getDashboard` callers. This was flagged in Review 2 item 4d and remains unresolved.

**4c — Cross-agency isolation test is described in prose but absent from the actual `FamilyPortalDashboardControllerIT` code block (Review 2 item 4f: unresolved)**

Task 9 Step 2b includes the requirement: "Add an integration test assertion in `FamilyPortalDashboardControllerIT`: when a valid portal JWT for agency A calls `/family/portal/dashboard`, data from agency B's client is never returned." This test does not appear anywhere in the `FamilyPortalDashboardControllerIT` code block in Task 13. Without it the cross-tenant isolation guarantee is untested. Add a concrete `dashboard_crossAgency_doesNotLeakShifts` test to Task 13 Step 2.

**4d — `ZoneId.of(agency.getTimezone())` will throw `ZoneRulesException` for invalid timezone strings (Review 2 Q3: unresolved)**

`getDashboard` calls `ZoneId.of(tz)` where `tz` comes directly from `agency.getTimezone()`. A corrupt, missing, or legacy timezone string (e.g., `"EST"` instead of `"America/New_York"`) will throw `java.time.zone.ZoneRulesException`, which is uncaught and will surface as a 500. Either validate `Agency.timezone` during persistence (a `@Pattern` or `@Size` constraint that enforces IANA format), or catch `ZoneRulesException` here and fall back to `ZoneOffset.UTC` with a warning log. The plan currently documents this question (Q3) but takes no action.

**4e — `PortalVerifyResponse.agencyId` field is returned to the frontend but never asserted in any test**

`PortalVerifyResponse` carries `jwt`, `clientId`, and `agencyId`. The `inviteThenVerify_happyPath_returnsPortalJwt` test only asserts `jwt` and `clientId`. `agencyId` is never validated in any test case. Since the frontend uses `agencyId` for context, and an incorrect value here would silently break the portal session, a simple `assertThat(verifyResp.getBody().agencyId()).isNotBlank()` at minimum should be added.

**4f — `evvRepo.findByShiftId` in `buildLastVisitDto` is an unbatched EVV lookup that will become a second per-last-visit query**

`buildLastVisitDto` calls `evvRepo.findByShiftId(shift.getId())`. For the last-visit path this fires exactly one time (since only one COMPLETED shift is fetched), but it is structurally identical to the pattern that caused the N+1 in `buildTodayVisitDto` before the fix. If `getDashboard` ever evolves to support more than one last-visit entry, this will silently become an N+1. Consider fetching the EVV record for the today-shift and last-visit shift in a single `evvRepo.findAllByShiftIdIn(shiftIds)` call alongside the bulk caregiver fetch, building an `evvMap`, and passing it into `buildTodayVisitDto` and `buildLastVisitDto`. This is a forward-looking improvement, not an immediate blocker.

---

## 5. Questions for Clarification

**Q1:** `FamilyPortalTokenRepository.findByTokenHash` — used in `verifyToken` — is also not shown in the plan and was introduced in Plan 1. Is it declared as a Spring Data derived method or a `@Query`? If it does not exist in Plan 1, the verify flow will fail at startup for the same reason as `deleteExpired`. The plan should confirm or cross-reference where both of these `FamilyPortalTokenRepository` methods are declared.

**Q2:** `@Sql` in `FamilyPortalDashboardControllerIT` truncates `evv_records` and `shifts`. If the schema has a FK from `shifts` to `recurrence_patterns`, will `CASCADE` handle orphan rows from other IT tests that run before this class? Or does each IT test class run in its own fresh Testcontainers instance? Clarifying the isolation boundary would remove ambiguity about whether the `@Sql` teardown is sufficient.

---

## 6. Final Recommendation

**Approve with changes.**

The two critical issues from Review 2 (Issue A and Issue B) are fully resolved. The plan now correctly bulk-fetches caregiver/service-type data and enforces exhaustive switch coverage. However, two new blockers remain before execution: **Issue C** (direct `TenantContext.get()` in the controller introduces a null-agencyId corruption path and violates the architectural convention of reading agency ID from `UserPrincipal`) and **Issue D** (missing `deleteExpired` method on `FamilyPortalTokenRepository`, which is a guaranteed startup failure). Both are one-line-to-a-few-line fixes. The minor items 4a (timestamp formatting), 4b (agencyId predicates on upcoming/last-visit queries), and 4c (cross-agency isolation test) have been carried forward from prior reviews without resolution and should be included in the same revision rather than deferred further.
