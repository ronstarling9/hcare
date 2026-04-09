# Critical Implementation Review — Plan 2: Family Portal Backend Endpoints
**Review #4** | Reviewer: Claude Sonnet 4.6 | Date: 2026-04-08

---

## 1. Overall Assessment

Both critical issues from Review 3 — the null-`agencyId` path via direct `TenantContext.get()` in `ClientController` (Issue C) and the missing `deleteExpired` declaration in `FamilyPortalTokenRepository` (Issue D) — are now fully resolved. `inviteFamilyPortalUser` correctly uses `@AuthenticationPrincipal UserPrincipal principal` and `principal.getAgencyId()`, and Task 12 Step 1 now explicitly adds the required `@Modifying @Query` method. However, the dead-code/`UnsupportedOperationException` block in `inviteThenVerify_happyPath_returnsPortalJwt` that was reported as resolved in Review 3's "Previously Addressed" section is still present in the plan. Review 3 marked it as fixed but the actual code block was not updated. Additionally, five minor items carried forward across reviews 1–3 remain unresolved.

---

## 2. Critical Issues

### Issue E — Dead code block in `inviteThenVerify_happyPath_returnsPortalJwt` causes `UnsupportedOperationException` at runtime (reported as resolved in Review 2 item 4a and Review 3 "Previously Addressed" — NOT actually fixed)

**What:** Lines 1013–1015 of the plan:
```java
HttpEntity<String> inviteReq = new HttpEntity<>(
    "{\"email\":\"family@example.com\"}", adminAuth());
inviteReq.getHeaders().set("Content-Type", "application/json");
```
Review 2 (item 4a) flagged these three lines as dead code. Review 3 listed this as resolved in the "Previously Addressed" section. The lines are still present in the current plan draft. They are not merely dead code — they will throw `UnsupportedOperationException` at runtime. `HttpEntity.getHeaders()` returns the headers wrapped as an unmodifiable view (via `HttpHeaders.readOnlyHttpHeaders()`). Calling `.set(...)` on that view throws `UnsupportedOperationException` before the `restTemplate.exchange` call is even reached, causing the test to fail at setup rather than at the assertion.

**Why it matters:** This is a test-blocking defect. The `inviteThenVerify_happyPath_returnsPortalJwt` test is the primary happy-path test for the invite-and-verify flow. If it throws before reaching its assertions, the test shows as a failure with a confusing non-assertion stack trace, masking any real bug in the feature under test.

**Fix:** Delete lines 1013–1015 entirely. The `restTemplate.exchange` call immediately after already uses a fresh inline `new HttpEntity<>("{\"email\":\"family@example.com\"}", adminAuth())`. The deleted lines serve no purpose:

```java
// BEFORE (lines 1013–1015 must be removed):
HttpEntity<String> inviteReq = new HttpEntity<>(
    "{\"email\":\"family@example.com\"}", adminAuth());
inviteReq.getHeaders().set("Content-Type", "application/json");  // UnsupportedOperationException
ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
    "/api/v1/clients/" + clientId + "/family-portal-users/invite",
    HttpMethod.POST,
    new HttpEntity<>("{\"email\":\"family@example.com\"}", adminAuth()),
    InviteResponse.class);

// AFTER (correct):
ResponseEntity<InviteResponse> inviteResp = restTemplate.exchange(
    "/api/v1/clients/" + clientId + "/family-portal-users/invite",
    HttpMethod.POST,
    new HttpEntity<>("{\"email\":\"family@example.com\"}", adminAuth()),
    InviteResponse.class);
```

Note: the `adminAuth()` helper does not set `Content-Type`. This is fine for tests using `TestRestTemplate` with a `PortalVerifyRequest` record body (Jackson serialises it automatically), but the `invite` endpoint receives a raw JSON string body. If the test fails with a 415 Unsupported Media Type, the fix is to add `Content-Type` to `adminAuth()` or to construct the `HttpEntity` with properly configured headers — not by calling `.set(...)` on the unmodifiable headers returned by `getHeaders()`.

---

## 3. Previously Addressed Items

The following issues from the full review history are resolved in this revision:

- **Review 1 Issue 1 (N+1 for upcoming visits)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 2 (missing `permitAll` on verify endpoint)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 3 (rate limiter X-Forwarded-For spoofing)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 4 (missing `findByClientIdAndAgencyIdAndEmail`)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 5 (90-day in-memory window)** — resolved in Review 2; confirmed intact.
- **Review 1 Issue 6 (fragile token extraction in tests)** — resolved in Review 2; confirmed intact.
- **Review 2 Issue A (residual N+1 in `buildTodayVisitDto`)** — resolved in Review 3; confirmed intact.
- **Review 2 Issue B (`mapShiftStatus` exhaustiveness / `MISSED` arm)** — resolved in Review 3; confirmed intact.
- **Review 3 Issue C (`TenantContext.get()` null-agencyId in `ClientController`)** — now fixed: Task 11 Step 3 uses `@AuthenticationPrincipal UserPrincipal principal` and `principal.getAgencyId()`.
- **Review 3 Issue D (`deleteExpired` not declared in `FamilyPortalTokenRepository`)** — now fixed: Task 12 Step 1 adds the `@Modifying @Query` declaration with required imports.

---

## 4. Minor Issues & Improvements

The following items have been carried forward from prior reviews without resolution. They are reproduced here for completeness and should be addressed in this revision rather than deferred to a fifth pass.

**4a — `LocalDateTime.toString()` produces zone-less strings on all timestamp fields (Review 1 item 4a, Review 2 item 4c, Review 3 item 4a: still unresolved)**

`buildTodayVisitDto` uses `evv.get().getTimeIn().toString()` and `.getTimeOut().toString()` for `clockedInAt`/`clockedOutAt`, and `shift.getScheduledStart().toString()` / `shift.getScheduledEnd().toString()` for the scheduled times. `buildUpcomingDto` and `buildLastVisitDto` do the same. `LocalDateTime.toString()` emits `2026-04-08T09:00` (no zone, optional seconds) instead of the "UTC ISO-8601" string the DTO contract specifies. `generateInvite` correctly uses `.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)` for `expiresAtStr`. All other timestamp fields must follow the same pattern. This has been raised in every review and remains unfixed.

**4b — `findTop3` and `findFirstByClientId...` derived queries still lack `agencyId` predicates (Review 2 item 4d, Review 3 item 4b: still unresolved)**

`findTop3ByClientIdAndStatusNotInAndScheduledStartAfterOrderByScheduledStartAsc` and `findFirstByClientIdAndStatusOrderByScheduledStartDesc` use only `clientId`. The plan's own rationale for adding an explicit `agencyId` predicate to `findByClientIdAndAgencyIdAndScheduledStartBetween` was that the Hibernate `agencyFilter` may not activate for `FAMILY_PORTAL`-role requests. That reasoning applies equally to both of these derived queries. Rename them to include `AgencyId` and pass `agencyId` from `getDashboard`. This was flagged in Review 2 and Review 3 without resolution.

**4c — Cross-agency isolation test is described in prose but absent from the `FamilyPortalDashboardControllerIT` code block (Review 2 item 4f, Review 3 item 4c: still unresolved)**

Task 9 Step 2b requires: "Add an integration test assertion in `FamilyPortalDashboardControllerIT`: when a valid portal JWT for agency A calls `/family/portal/dashboard`, data from agency B's client is never returned." No such test appears in the `FamilyPortalDashboardControllerIT` code block in Task 13. A concrete `dashboard_crossAgency_doesNotLeakShifts` test should be added to Task 13 Step 2.

**4d — `ZoneId.of(agency.getTimezone())` will throw `ZoneRulesException` for invalid or legacy timezone strings (Review 2 Q3, Review 3 item 4d: still unresolved)**

`getDashboard` calls `ZoneId.of(tz)` where `tz` comes directly from `agency.getTimezone()`. A corrupt, missing, or legacy timezone abbreviation (e.g., `"EST"`) throws `java.time.zone.ZoneRulesException`, which surfaces as 500. The plan poses this as Q3 but takes no action. Either add a `@Pattern` constraint on `Agency.timezone` to enforce IANA format at persistence time, or catch `ZoneRulesException` and fall back to `ZoneOffset.UTC` with a warning log. The former is safer for production correctness.

**4e — `PortalVerifyResponse.agencyId` is never asserted in any test (Review 3 item 4e: still unresolved)**

`inviteThenVerify_happyPath_returnsPortalJwt` asserts `jwt` and `clientId` but not `agencyId`. Since the frontend uses `agencyId` to establish portal session context, a null or incorrect `agencyId` in the JWT would silently break subsequent dashboard calls. Add `assertThat(verifyResp.getBody().agencyId()).isNotBlank()` at a minimum.

**4f — EVV lookup in `buildLastVisitDto` is an unbatched point query (Review 3 item 4f: still present)**

`buildLastVisitDto` calls `evvRepo.findByShiftId(shift.getId())` as a standalone query. While it only fires once for the last-visit path today, it follows the same structural pattern as the per-shift N+1 that was fixed in the upcoming/today paths. Extending the bulk EVV fetch to cover the today-shift and last-visit shift (using `evvRepo.findAllByShiftIdIn(shiftIds)`) and passing the resulting `evvMap` into both `buildTodayVisitDto` and `buildLastVisitDto` is a forward-looking improvement that prevents a silent N+1 regression if these methods are ever reused.

---

## 5. Questions for Clarification

**Q1:** The `adminAuth()` helper in `FamilyPortalAuthControllerIT` does not set `Content-Type: application/json`. The invite endpoint receives a raw JSON string body (`"{\"email\":\"family@example.com\"}"`) via `HttpEntity<String>`. `TestRestTemplate` may not infer `Content-Type` from a `String` body. If the test fails with `415 Unsupported Media Type`, the correct fix is to set content type in `adminAuth()` or in the entity constructor — not via `getHeaders().set(...)`. Should `adminAuth()` include `setContentType(MediaType.APPLICATION_JSON)` for consistency with `obtainPortalJwt()` in `FamilyPortalDashboardControllerIT`, which does explicitly set content type?

**Q2:** The `@Sql` teardown in `FamilyPortalAuthControllerIT` truncates `family_portal_tokens, family_portal_users, clients, agency_users, agencies`. It does not truncate `shifts`, `evv_records`, `service_types`, or `caregivers`. Is this intentional (the auth IT does not seed shifts)? If other IT classes seed shifts for the same client IDs and share the same Testcontainers instance without isolation, stale shift rows could affect auth tests that indirectly call `getDashboard`. Clarifying the Testcontainers isolation boundary (shared instance per test class, or per test method) would confirm whether the truncation scope is sufficient.

---

## 6. Final Recommendation

**Approve with one blocking fix.**

The two critical issues from Review 3 (Issue C and Issue D) are fully resolved. One new blocking issue remains: **Issue E** — the dead code block in `inviteThenVerify_happyPath_returnsPortalJwt` will throw `UnsupportedOperationException` on `inviteReq.getHeaders().set(...)`, causing the primary happy-path test to fail before reaching its assertions. This was reported as resolved in Review 3 but was not actually removed from the plan. The fix is a three-line deletion. The minor items (4a timestamp formatting, 4b agencyId predicates on derived queries, 4c cross-agency isolation test, 4d ZoneRulesException handling, 4e agencyId assertion in verify test) have now been carried forward across four review iterations without resolution and should be addressed in this same revision pass rather than deferred to a fifth.
