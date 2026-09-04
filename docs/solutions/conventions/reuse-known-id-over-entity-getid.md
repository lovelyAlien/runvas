---
title: Reuse an already-known ID instead of calling entity.getId() on a freshly-fetched JPA entity
date: 2026-09-04
category: conventions
module: backend/admin
problem_type: convention
component: service_object
severity: low
applies_when:
  - "A method fetches a JPA entity by an id it already holds as a local value, and needs that same id again later in the method"
  - "Writing or reviewing a Mockito-based unit test that constructs a JPA entity via a factory method (not through the real persistence context) where the code under test calls entity.getId() or another @GeneratedValue field getter"
  - "Reviewing code where entity.getId() is called shortly after the same id value was already parsed or computed locally"
related_components: [AdminReportActionService, AdminReportActionServiceTest, TokenBlacklistService, KakaoAuthServiceTest]
tags: [jpa, hibernate, mockito, unit-testing, entity-id, code-review, service-layer]
---

# Reuse an already-known ID instead of calling entity.getId() on a freshly-fetched JPA entity

## Context

`AdminReportActionService.resolveAndBan()` fetches a `User` via `userRepository.findById(authorUuid)` and, inside that same lambda, originally called `user.getId()` again to pass the author's UUID to `tokenBlacklistService.banUser(...)`. Because `User.id` is a JPA `@GeneratedValue` field (`backend/src/main/java/com/runvas/user/domain/User.java:19-21`) that Hibernate only populates as part of real persistence-context machinery (an actual `INSERT` or a load from the database), a `User` built purely through the `createKakaoUser(...)` factory (`User.java:63-72`) and handed back by a Mockito-mocked `UserRepository` had `id == null` — a mock never invokes that machinery, it just returns whatever object a test wired into `when(...).thenReturn(...)`.

This surfaced as a real test failure while implementing a plan (`docs/superpowers/plans/2026-09-04-ban-invalidates-active-jwt.md`, Task 4): `AdminReportActionServiceTest.resolveAndBanDeletesContentAndBansAuthor` failed on `verify(tokenBlacklistService).banUser(authorUuid)` with a Mockito argument-mismatch of the shape `Wanted: banUser(<uuid>); Actual: banUser(null)` — because the production code called `user.getId()` on a mock-constructed `User` whose `id` was never set.

The first fix (adding `ReflectionTestUtils.setField(author, "id", authorUuid)` to the test) worked and was accepted at task-level review — it's the same pattern already used in `backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java`'s `persisted(User user)` helper (lines 149-154) to simulate a "loaded from the database" entity. But `resolveAndBan()` already computes `UUID authorUuid = UUID.fromString(authorId)` one line earlier, purely to perform the `findById(...)` lookup. Reusing that local variable for the later `banUser(...)` call removed the redundant `user.getId()` call and made the `ReflectionTestUtils` workaround unnecessary in this test — the deeper fix, found in a later whole-branch review, treats the cause (a redundant getter call) rather than the symptom (the test can't produce a populated id).

## Guidance

When a method fetches a JPA entity by an id you already hold as a local value, and needs that same id again later in the method, keep it in a local variable instead of calling `entity.getId()` a second time.

Before (`backend/src/main/java/com/runvas/backend/admin/AdminReportActionService.java`, prior state):

```java
if (authorId != null) {
    userRepository.findById(UUID.fromString(authorId)).ifPresent(user -> {
        user.ban();
        userRepository.save(user);
        tokenBlacklistService.banUser(user.getId());
    });
}
```

After (current code as of this writing, `AdminReportActionService.java:73-80`, on branch `fix/ugc-ban-token-invalidation`, not yet merged):

```java
if (authorId != null) {
    UUID authorUuid = UUID.fromString(authorId);
    userRepository.findById(authorUuid).ifPresent(user -> {
        user.ban();
        userRepository.save(user);
        tokenBlacklistService.banUser(authorUuid);
    });
}
```

`UUID.fromString(authorId)` is computed once into `authorUuid`, used for both the `findById` lookup and the later `banUser` call. `user.getId()` is no longer called anywhere in this method.

When a test's production-code path genuinely needs to read a populated `@GeneratedValue` id back off a mock-constructed entity (i.e. the "reuse the local value" guidance above doesn't apply because there is no already-known local value), use `ReflectionTestUtils.setField(entity, "id", value)` — an established, accepted pattern in this repo. See `KakaoAuthServiceTest`'s `persisted(User user)` helper (lines 149-154), which sets `id`, `createdAt`, and `updatedAt` together to simulate a "loaded from the database" entity.

## Why This Matters

`@Id @GeneratedValue private UUID id;` (`User.java:19-21`) tells Hibernate to assign `id` only through real persistence-context machinery. A Mockito `mock(UserRepository.class)` never runs that machinery, so any entity built through a plain factory method that never assigns `id` — like `createKakaoUser(...)` — keeps `id == null` unless a test forces it in some other way.

A locally-held id parsed from the same input (a path variable, a request field, another service's lookup result) is exactly as trustworthy as the entity's populated `id` would be in production — it's the same value, just not routed through a second getter call. Reusing it sidesteps the entity-id-population question entirely rather than working around it, which also means one less thing a unit test has to fake. The redundant `entity.getId()` call was invisible in production (JPA always fills `id` on load there) and only became a problem in a mock-based test — exactly the kind of latent trap that's cheap to avoid by not writing the redundant call in the first place.

## When to Apply

- A method fetches an entity by an id you already hold as a local value (parsed from a path variable, a request field, or returned by another lookup), and needs that id again later in the same method — reuse the local value instead of calling `entity.getId()`.
- Reviewing code that calls `entity.getId()` (or any getter on a JPA `@GeneratedValue` field): check whether the method already had that same id as a local value one line above. If so, the `getId()` call is redundant and worth flagging — a cheap, roughly grep-able pattern (`getId()` calls inside a block that recently computed the same id).
- Writing a unit test against a mocked repository that returns an entity built via a factory method: if the code under test calls `entity.getId()`, first check whether that call is actually necessary (per the point above) before reaching for `ReflectionTestUtils.setField(entity, "id", ...)` to paper over it.

## Examples

Removing the redundant getter call also simplified the test — the `ReflectionTestUtils` import and `setField` call became unnecessary:

```java
// backend/src/test/java/com/runvas/backend/admin/AdminReportActionServiceTest.java:117-118 (current)
User author = User.createKakaoUser(authorUuid.toString(), null, "Author", null);
when(userRepository.findById(authorUuid)).thenReturn(Optional.of(author));
```

`author.getId()` is never read by production code anymore, so the mock entity's `id` field can stay `null` throughout the test, and `verify(tokenBlacklistService).banUser(authorUuid)` still passes because `authorUuid` was never derived from the entity in the first place.

Contrast with a case where the pattern from "Guidance" genuinely doesn't apply — `KakaoAuthServiceTest`'s `persisted(User user)` helper needs `ReflectionTestUtils.setField(user, "id", UUID.randomUUID())` because the code under test there has no other source for that id; it must read it off the entity.

## Related

- `backend/src/test/java/com/runvas/auth/service/KakaoAuthServiceTest.java` — the existing precedent for the `ReflectionTestUtils.setField(entity, "id", ...)` pattern in this codebase (`persisted(User user)` helper, lines 149-154). It remains the right tool when a test's production code path genuinely needs to read a populated `@GeneratedValue` id back off a mock-constructed entity — this incident didn't invalidate that pattern, it just showed that `AdminReportActionService.resolveAndBan()` didn't actually need it once the redundant `user.getId()` call was removed.
