---
title: Ban marker TTL assumed JWT expiration config never decreases
date: 2026-09-04
category: security-issues
module: backend/auth
problem_type: security_issue
component: authentication
symptoms:
  - "A banned user's pre-existing JWT would be accepted again by JwtAuthenticationFilter once the Redis ban marker expired, even though the JWT itself had not expired yet"
  - "The auth:banned-user:<userId> Redis key could expire before some already-issued tokens do, silently reopening access for a banned user"
  - "The gap only appears after an operator lowers JWT_EXPIRATION_SECONDS (e.g. 3600 -> 900) and a ban is issued after that change; not reproducible under a static config, so it wasn't caught by unit tests"
  - "No error, log, or alert would signal the marker's early expiry — the regression would be silent until someone noticed the banned user acting again"
root_cause: config_error
resolution_type: code_fix
severity: high
related_components: [TokenBlacklistService, JwtAuthenticationFilter, JwtProvider]
tags: [security, jwt, redis, ttl, token-invalidation, ban, config-change]
---

# Ban marker TTL assumed JWT expiration config never decreases

## Problem

`TokenBlacklistService.banUser(UUID userId)` computed the Redis ban-marker's TTL directly from `jwtProvider.getExpirationSeconds()` — the *currently configured* JWT lifetime read fresh at ban time — rather than from any bound on how long a token issued in the past could still be valid.

## Symptoms

This bug never actually fired in production — it was caught by a reviewer during the final whole-branch review of the branch that introduced it, before the vulnerable version shipped (see `docs/superpowers/specs/2026-09-04-ban-invalidates-active-jwt-design.md:5`, "최종 리뷰에서 발견").

Had it shipped as originally written, the observable failure would have been: an operator lowers `JWT_EXPIRATION_SECONDS` (backed by `runvas.jwt.expiration-seconds`, default `3600`, `backend/src/main/resources/application.yml:54`) — say from 3600 to 900 — at some point after a token was issued under the old, longer value. If a ban is applied *after* that config change, `banUser` computes the marker's TTL from the *new*, shorter 900s value. Since the pre-change token can still be valid for up to the *old* 3600s from its own issuance, the `auth:banned-user:<userId>` Redis marker could expire before that token does. `JwtAuthenticationFilter` (`backend/src/main/java/com/runvas/global/security/JwtAuthenticationFilter.java:35`) only denies a request while the marker exists — once it expires, a banned user's write access (posting, commenting) would silently return to normal for the remainder of that token's original lifetime, with no error, no log line, and nothing marking the moment it happened.

## What Didn't Work

There was no failed prior fix attempt here — this was caught by design review before any incorrect version shipped, not through trial-and-error. It's worth being explicit about why a normal test suite wouldn't have caught it: a unit test for `banUser` mocks `jwtProvider.getExpirationSeconds()` to a constant and asserts the TTL matches that constant — it has no way to simulate "the config value used at ban time is smaller than the config value that was in effect when some still-valid token was minted," because that requires two different points in time with two different config values, and ordinary unit tests only exercise one config snapshot per run.

The actual investigative gap is in code review, not testing: it's easy to look at `Duration.ofSeconds(jwtProvider.getExpirationSeconds())` and see that it correctly bounds "how long a token issued *right now* stays valid," and stop there — because that's true. The bug only surfaces when you ask a second question the code doesn't answer: "what happens if this config value changes after some data (the token) was already created using the old value?" That question is what a normal review pass, focused on whether the code does what it says, tends to skip — it requires reasoning about config mutability across time, not just correctness of the formula for a single point in time.

## Solution

Before (`backend/src/main/java/com/runvas/auth/service/TokenBlacklistService.java`, pre-fix):

```java
public void banUser(UUID userId) {
    redisTemplate.opsForValue().set(
            USER_BAN_KEY_PREFIX + userId, "1", Duration.ofSeconds(jwtProvider.getExpirationSeconds()));
}
```

After (current code as of this writing, `TokenBlacklistService.java:36-39`, on branch `fix/ugc-ban-token-invalidation`, not yet merged):

```java
public void banUser(UUID userId) {
    Duration ttl = Duration.ofSeconds(jwtProvider.getExpirationSeconds()).plusHours(1);
    redisTemplate.opsForValue().set(USER_BAN_KEY_PREFIX + userId, "1", ttl);
}
```

`getExpirationSeconds()` is a plain getter over the constructor-injected `expirationSeconds` field on `JwtProvider`, sourced from `@Value("${runvas.jwt.expiration-seconds}")` (`backend/src/main/java/com/runvas/auth/service/JwtProvider.java:19-23`), which resolves to `runvas.jwt.expiration-seconds: ${JWT_EXPIRATION_SECONDS:3600}` in `application.yml:54`.

## Why This Works

A fixed one-hour margin means the marker's effective lifetime is `(configured-TTL-at-ban-time + 1 hour)`, not just `configured-TTL-at-ban-time`. As long as that margin is larger than any plausible amount the config value could have been decreased by between when the oldest still-valid token was issued and when the ban is applied, the marker outlives every token that could still be valid at ban time — so `tokenBlacklistService.isUserBanned(userId)`, checked in `JwtAuthenticationFilter.java:35`, keeps denying the request for the entire window during which any pre-ban token could still pass signature/expiry checks.

Critically, this doesn't require tracking historical config values, auditing deploy history, or knowing when `JWT_EXPIRATION_SECONDS` last changed — it just builds in slack against the entire class of problem. The design doc records the same reasoning: "안전 마진을 두는 이유: TTL을 정지 시점의 설정값 그대로 쓰면, 운영자가 이후 JWT_EXPIRATION_SECONDS를 더 짧게 변경했을 때 그 변경 이전에 발급된(더 긴 수명의) 토큰이 마커보다 먼저 만료되지 않을 수 있어 이 변경이 막으려는 문제가 그대로 재현된다" (`docs/superpowers/specs/2026-09-04-ban-invalidates-active-jwt-design.md:30-32`).

## Prevention

When a security-relevant TTL or expiry is *derived from a config value* rather than from a value actually bound to the thing it protects, ask at review time: "if this config value changes between when the thing being protected was created and when this new marker/record is written, does the marker still outlive it?" If the answer isn't obviously yes, either bind the TTL to the actual artifact's own recorded expiry (not the current config) or add an explicit safety margin sized to the largest plausible config change.

This is a broader pattern than JWT/Redis specifically — anywhere a TTL or expiry is computed as "read current config, use it as the lifetime of something tied to past state" has the same shape:
- Cache invalidation keyed to a cache-version config that can be bumped or lowered independently of what's already cached.
- Session timeouts computed at session-creation time from a "max session length" config that gets shortened later, leaving already-issued sessions valid longer than a newly-written control record assumes.
- Any revocation/deny-list marker whose lifetime is copied from a "how long do these live" config instead of being pinned to the actual maximum lifetime of the specific artifacts already in flight.

The generalizable review heuristic: treat "config value used to bound something created in the past" as a potential invariant violation, and prefer either (a) deriving the TTL from the artifact's own recorded expiry when available, or (b) a fixed margin large enough to absorb realistic config drift, over (c) trusting that the config value read "now" was also the value in effect when the oldest still-relevant artifact was created.

Note that this codebase's existing token-level blacklist, `TokenBlacklistService.blacklist(String token)` (`TokenBlacklistService.java:23-30`), already follows option (a): it computes its TTL from `jwtProvider.getExpiration(token)` — the specific token's own `exp` claim, parsed from the token itself — not from the live config value, so it does not have this bug. `banUser` couldn't use that approach because a ban is keyed by user, not by a specific token, so there is no single token to read an `exp` claim from; that's exactly why it needed option (b), the fixed margin, instead.

## Related Issues

The same rationale is recorded in Korean as part of this feature's design documentation, in the "영향 범위" section of `docs/superpowers/specs/2026-09-04-ban-invalidates-active-jwt-design.md:30-32`, which explains why the TTL needs the safety margin in terms of the `JWT_EXPIRATION_SECONDS` config being lowered after a ban-eligible token was already issued.

See also [Reuse an already-known ID instead of calling entity.getId() on a freshly-fetched JPA entity](../conventions/reuse-known-id-over-entity-getid.md) — a separate lesson from the same branch and the same final whole-branch review, unrelated in root cause but from the same review pass.
