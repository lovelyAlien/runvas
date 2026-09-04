---
title: A New Config Variable Needs an Explicit Deploy-Wiring Task, Not Just an App-Config Task
date: 2026-09-04
category: workflow-issues
module: task-planning
problem_type: workflow_issue
component: development_workflow
severity: high
applies_when:
  - "A multi-task implementation plan introduces new secret/config environment variables consumed by application code (e.g. via ${VAR:} defaults in application.yml or similar)"
  - "The plan was produced by a single planning pass without per-file ownership, so no task explicitly owns updating deploy-plumbing files (docker-compose.yml environment allowlists, .env.example templates, README.md env-var docs)"
  - "Task-level code review checks each task's diff strictly against that task's own brief, with no task or review step that traces the config end-to-end from application code to deployed container"
  - "The consuming code is written best-effort (catch/log/continue) so a missing or empty config value fails silently instead of crashing, removing any runtime signal that plumbing was incomplete"
  - "Only a final whole-branch review (run once after all tasks complete) is positioned to ask a cross-task question like 'where is the deploy-config equivalent of the other existing secrets for this new one?'"
symptoms:
  - "Every task-level review of the task that added new env vars to application.yml passed cleanly - the diff exactly matched its own task brief"
  - "docker-compose.yml's backend service environment block (an explicit allowlist, not a host-env passthrough) was never updated to include the three new secret keys"
  - ".env.example, backend/.env.example, and backend/README.md's documented env-var list were likewise never updated"
  - "No task in the plan, and no per-task review, ever referenced docker-compose.yml, .env.example, or README.md, because the plan's single planning pass never listed 'update deploy plumbing' as a task or as a step of any task"
root_cause: missing_workflow_step
resolution_type: workflow_improvement
related_components: [tooling, documentation, development_workflow]
tags: [task-decomposition, whole-branch-review, deploy-config-plumbing, env-vars, silent-failure, plan-scoping, code-review-gate]
---

# A New Config Variable Needs an Explicit Deploy-Wiring Task, Not Just an App-Config Task

## Context

A 9-task implementation plan (`docs/superpowers/plans/2026-09-04-apple-token-revocation.md`) added Apple Sign-In token revocation on account deletion, closing an App Store Review Guideline 5.1.1(v) compliance gap. The feature needed three new secret environment variables read at runtime: `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`.

Task 3 of the plan added the three keys to `backend/src/main/resources/application.yml` as `${APPLE_TEAM_ID:}`, `${APPLE_KEY_ID:}`, `${APPLE_PRIVATE_KEY:}` — safe empty-string defaults, matching the file's existing style for `APPLE_BUNDLE_ID`/`APPLE_JWKS_URI` and the Kakao equivalents — plus an `AppleClientSecretGenerator` component that reads them to sign a JWT client secret for Apple's REST API. The dedicated task-level reviewer dispatched after Task 3 checked that task's diff against that task's brief and passed it cleanly: the brief said "add these three keys to `application.yml`," and the diff did exactly that, nothing more and nothing less.

No task in the plan, and therefore no task-level review, ever checked whether the same three variables were also wired through the rest of the actual deployment path:

- `docker-compose.yml`'s `backend` service `environment:` block, which is an explicit **allowlist** — it names each variable one by one (`KAKAO_REST_API_KEY: ${KAKAO_REST_API_KEY}`, `KAKAO_CLIENT_SECRET: ${KAKAO_CLIENT_SECRET}`, `TMAP_APP_KEY: ${TMAP_APP_KEY}`, etc.), not a passthrough of the whole host environment into the container. A variable set on the host or in CI is invisible inside the container unless it has its own line here.
- `.env.example` and `backend/.env.example`, the local-dev environment variable templates, which list every other secret this way but hadn't been updated for the new three.
- `backend/README.md`'s `## Required Environment` / `## Optional Environment` sections, which already documented `APPLE_BUNDLE_ID` and `APPLE_JWKS_URI` but never got the three new entries added alongside them.

This was not a review failure. Every task-level review did its job correctly on the scope it was given. It was a planning/task-decomposition gap: the plan never allocated "update `docker-compose.yml` / `.env.example` / README for the new env vars" as its own task or as an explicit step inside Task 3 (or any other task), so no task's brief — and therefore no task's review — ever mentioned the deploy-plumbing files. Nobody's job, by construction, was to ask whether the new config value actually reaches a real deployment.

The gap was caught only by a final whole-branch review, run once after all 9 tasks were individually complete and individually reviewed clean, that deliberately looked at the feature's actual end-to-end runtime path rather than re-checking task-by-task diff conformance. The reviewer noticed the existing `KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`TMAP_APP_KEY` lines already present in `docker-compose.yml` and asked, in effect, "where's the equivalent wiring for the new Apple keys?" — a question that is structurally impossible for any single task-scoped review to ask, because no single task's diff contains both "the new config key" and "the file that's missing it."

Had it not been caught, the feature — whose entire purpose was App Store compliance — would have shipped completely non-functional, silently, forever. The config-reading component was deliberately written to fail closed rather than silently: on missing configuration it logs a warning and throws, but its callers (the login and account-purge flows) catch that exception, log it, and proceed normally — specifically so missing Apple configuration could never crash the app or block login/deletion for unrelated reasons. That correct, necessary design choice is exactly what would have made the deploy-wiring gap invisible in production — no crash, no error, no user-facing symptom, just a warning line in logs nobody was watching, and a compliance feature that silently never worked.

The fix, once found, was trivial: add the three env vars to `docker-compose.yml`, both `.env.example` files, and `backend/README.md`, in the same style as the existing `KAKAO_*` entries. One config-and-docs-only change, zero application code changes.

## Guidance

When a plan adds a **new secret/config value** that a running service will read from its environment, "add it to the application's own config file" (e.g. `application.yml`, `settings.py`, an app's internal `.env` parsing) is **necessary but not sufficient**. Trace every layer between "how a human or CI actually sets the value" and "the process that reads it," and give each layer its own explicit task or step — do not fold it silently into the app-config task and assume it's covered.

Add this checklist item to plan-writing and plan-review practice, applied every time a plan introduces a new environment variable:

> Does this task introduce a new environment variable? If yes, does the plan also have an explicit step that adds it to **every** deployment/config-delivery surface the app has — not just the app's own settings file? For a docker-compose-deployed service that means: the compose file's `environment:` allowlist, the `.env.example` templates, and the README's documented-env-vars section. For other deployment shapes, the equivalent is a Kubernetes manifest's `env:`/`envFrom:` block, a Terraform variable, a CI secrets configuration, or a PaaS dashboard's env var list.

Concretely, in this repo, a new backend secret env var (call it `NEW_SECRET`) needs a line in each of these four places, not just the first:

1. `backend/src/main/resources/application.yml` — `${NEW_SECRET:}` (the app-internal binding; this is the one task-level reviews naturally check because it's where the plan's feature code lives).
2. `docker-compose.yml`, inside the `backend` service's `environment:` block — `NEW_SECRET: ${NEW_SECRET}`, alongside the existing `KAKAO_REST_API_KEY: ${KAKAO_REST_API_KEY}` / `APPLE_TEAM_ID: ${APPLE_TEAM_ID}` / `TMAP_APP_KEY: ${TMAP_APP_KEY}` lines. This is the allowlist step that is easiest to forget because it lives in a file the feature's own code never touches.
3. `.env.example` (repo root) and `backend/.env.example` — an empty `NEW_SECRET=` line with a short comment on where to obtain the value, matching how `APPLE_TEAM_ID=` / `APPLE_KEY_ID=` / `APPLE_PRIVATE_KEY=` are documented in both files today.
4. `backend/README.md`'s `## Required Environment` or `## Optional Environment` section — one bullet naming the var and its effect if unset, matching the existing `APPLE_TEAM_ID` (Apple Developer Team ID; required for Apple token exchange/revoke to work — without it, Apple login still works but refresh tokens are never stored and account deletion never revokes Apple access) style entry.

When writing or reviewing a plan, if a task's brief says "add config key X to the app's settings file," ask explicitly whether a separate task or step also says "and wire X through the deployment path" — a plan should never leave that second half implicit.

## Why This Matters

Task-scoped reviews are reliable exactly because they're narrow: a reviewer checking one task's diff against that task's brief can be thorough and fast precisely by not looking at anything outside that scope. But that narrowness has a structural blind spot — it cannot see a gap that spans two files where only one of them appears in any single task's diff. A plan that never assigns "wire the new config through deployment" as explicit task-level work guarantees that blind spot will apply to that config value, no matter how careful each individual task-level review is.

The failure mode this produces is worse than a build error or a test failure: a silently-inert feature. Because well-designed config-reading code degrades gracefully (warn-and-continue rather than crash) when a secret is missing — which is the right behavior for resilience — a missing deploy-wiring step produces *zero* observable signal in normal operation. There's no failing test, no stack trace, no user complaint tied to a root cause. The only way to catch it is either a deliberate end-to-end review of the runtime path, or someone eventually noticing the compliance/feature outcome never actually happens. Relying on the latter is not an acceptable safety net for anything user-facing or compliance-critical.

## When to Apply

- A plan task adds a new environment variable, secret, or externally-supplied config value that a running service (not just a test or local script) will read.
- The app is deployed via any mechanism where "where the value is documented/consumed inside app code" and "where the value is actually injected into the running process" are different files or systems — docker-compose `environment:` blocks, Kubernetes manifests, Terraform variables, CI secret stores, PaaS env var dashboards.
- The plan's task breakdown assigns config-file wiring (e.g. `application.yml`) to one task but does not explicitly assign deployment-path wiring to any task.
- Reviewing a plan or a finished branch for a feature that depends on new secrets, especially before a final whole-branch review — ask directly whether the new variable appears in the deployment allowlist/manifest/CI config, not just the app's own settings file.
- The new config value feeds a best-effort / fail-open code path (logs a warning and continues rather than crashing when unset) — these are the cases most likely to ship silently broken, because there is no error to surface the gap.

## Examples

**Before (the gap, as it existed after Task 3 of the plan landed):**

`backend/src/main/resources/application.yml` correctly had:
```yaml
apple:
  team-id: ${APPLE_TEAM_ID:}
  key-id: ${APPLE_KEY_ID:}
  private-key: ${APPLE_PRIVATE_KEY:}
```

But `docker-compose.yml`'s `backend.environment:` block had no `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_PRIVATE_KEY` lines at all — only the pre-existing `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, and `TMAP_APP_KEY`. `.env.example`, `backend/.env.example`, and `backend/README.md` had no mention of the three new vars either. A container started from `docker-compose.yml` would run with all three Apple values empty, `AppleClientSecretGenerator` would log a warning and skip client-secret generation, and Apple token exchange/revocation would silently never function — while login and account deletion for other providers continued to work normally, masking the failure.

**After (the fix, now in place on this branch):**

`docker-compose.yml`, `backend` service, `environment:` block:
```yaml
      KAKAO_REST_API_KEY: ${KAKAO_REST_API_KEY}
      KAKAO_CLIENT_SECRET: ${KAKAO_CLIENT_SECRET}
      APPLE_TEAM_ID: ${APPLE_TEAM_ID}
      APPLE_KEY_ID: ${APPLE_KEY_ID}
      APPLE_PRIVATE_KEY: ${APPLE_PRIVATE_KEY}
      TMAP_APP_KEY: ${TMAP_APP_KEY}
```

Root `.env.example`:
```
KAKAO_REST_API_KEY=
KAKAO_CLIENT_SECRET=
APPLE_TEAM_ID=
APPLE_KEY_ID=
APPLE_PRIVATE_KEY=
TMAP_APP_KEY=
```

`backend/.env.example`:
```
APPLE_TEAM_ID=
APPLE_KEY_ID=
APPLE_PRIVATE_KEY=
```

`backend/README.md`, `## Optional Environment`:
```
- `APPLE_TEAM_ID` (Apple Developer Team ID; required for Apple token exchange/revoke to work — without it, Apple login still works but refresh tokens are never stored and account deletion never revokes Apple access)
- `APPLE_KEY_ID` (Key ID of the `.p8` Sign in with Apple private key)
- `APPLE_PRIVATE_KEY` (contents of the `.p8` private key; PEM format, literal `\n` for line breaks is accepted)
```

The generalizable shape of the fix: every new secret gets one line in the app-config file (Task 3's job) **and** one line in each of the compose allowlist, both `.env.example` templates, and the README's env-var section — four to five small, mechanical edits that a plan should list as their own explicit step rather than leave for someone to notice is missing.

## Related

- `docs/solutions/workflow-issues/subagent-worktree-verification.md` — a different lesson from the same session (subagent working-directory verification during dispatch), unrelated to this plan-decomposition/deploy-plumbing issue beyond sharing a category.
- `docs/superpowers/plans/2026-09-04-apple-token-revocation.md` — the 9-task plan whose Task 3 exhibited this gap.
- `superpowers:writing-plans`, `superpowers:subagent-driven-development` — the planning and execution skills in play when this gap was introduced and later caught.
