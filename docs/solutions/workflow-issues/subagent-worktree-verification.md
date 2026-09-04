---
title: Verify Working Directory as a Mandatory First Step When Dispatching Subagents Into an Existing Git Worktree
date: 2026-09-04
category: workflow-issues
module: subagent-dispatch
problem_type: workflow_issue
component: development_workflow
severity: high
applies_when:
  - "Dispatching implementer subagents (e.g. via a general-purpose Agent-dispatch tool) into a specific git worktree, in a repo that has multiple concurrent worktrees sharing one git object database with the primary checkout"
  - "Using a cheap/fast model tier (e.g. haiku) for subagent dispatch on tasks that will run git commands (especially git commit) in a specific working directory"
  - "The dispatch prompt conveys the required working directory only as prose scene-setting (e.g. 'Work from: <path>') rather than as a mandatory, self-verified first action"
  - "The controller is tempted to trust a subagent's own DONE/status report instead of independently verifying repo state"
  - "Any workflow where a subagent's own vantage point could look self-consistent even when it is operating in the wrong location (e.g. a stale branch missing a prerequisite feature looks like 'an earlier task hasn't landed yet')"
symptoms:
  - "A dispatched subagent runs git commit in the primary repository checkout instead of the assigned worktree, landing a stray commit on local main"
  - "The subagent invents/fabricates missing prerequisite code (e.g. a method that should already exist) because it is silently operating against the wrong branch and never notices"
  - "A retried subagent reports status NEEDS_CONTEXT, falsely claiming prerequisite code is missing, when it is actually present in the assigned worktree (hallucinated investigation result)"
root_cause: missing_workflow_step
resolution_type: workflow_improvement
related_components: [tooling]
tags: [subagent-orchestration, git-worktree, multi-agent-dispatch, working-directory-verification, model-tier-selection, self-verification]
---

# Verify Working Directory as a Mandatory First Step When Dispatching Subagents Into an Existing Git Worktree

## Context

This session ran a 9-task implementation plan via the `superpowers:subagent-driven-development` workflow, dispatching a fresh implementer subagent per task through a general-purpose Agent-dispatch tool, into a specific pre-existing git worktree
(`.claude/worktrees/exciting-cori-f85397`, branch `feat/apple-account-revoke`). The repo had 30+ concurrent worktrees checked out under `.claude/worktrees/`, all sharing one git object database with the primary repo checkout.

Task 2's dispatch prompt named the target directory only as prose scene-setting — a line like "Work from: `<worktree path>`" — with no instructed, self-verified check that the subagent was actually there before it started editing and committing. The subagent ran on a cheap/fast model tier ("haiku"). It never moved into the assigned worktree at all: it operated in the **primary repository checkout**, a shared workspace that happened to have unrelated, uncommitted, in-progress work from a separate session already sitting in it (one modified file plus two untracked planning docs). The subagent ran `git commit` there, landing a stray commit (`533c2a1`, later reverted via `git revert` — the commit itself remains an ancestor of `main`, as a revert undoes a diff rather than removing the original commit from history; cited here only as the incident's specific identifier) on local `main`.

Because the primary checkout's local `main` was stale (it hadn't pulled the merge of an unrelated PR, `feat/apple-sign-in`, that the assigned worktree's branch was built on top of — the PR had already merged on GitHub by this point, the local checkout just hadn't fetched it), the subagent hit what looked like a normal mid-task gap — a missing `User.createAppleUser(...)` method — and simply invented it from scratch to keep going. Its own DONE_WITH_CONCERNS report flagged the invented method as an unplanned addition, but never identified the actual cause: it was in the wrong repository checkout and the wrong branch entirely. Nothing in the subagent's self-report distinguished this from ordinary, correct work.

The controller caught the problem only by independently running its own verification commands (`pwd`, `git rev-parse --show-toplevel`, `git branch --show-current`, `git log`, `git diff`) rather than trusting the subagent's report. Recovery used `git revert --no-commit 533c2a1` on the primary checkout — deliberately not `git reset --hard`, which would also have destroyed the unrelated pending uncommitted work sharing that checkout. The revert surgically restored exactly the 4 touched files (confirmed via an empty `git diff` against the pre-incident base) while leaving the checkout's unrelated uncommitted changes completely untouched.

A second attempt at the same task, still on the cheap model but with a hardened prompt asking it to check `pwd`/branch before editing, failed differently: the subagent correctly ran the verification commands and they correctly confirmed the right worktree and branch — but it then reported `NEEDS_CONTEXT`, falsely claiming that prerequisite code (the `AuthProvider.APPLE` enum value, `User.createAppleUser(...)`) was missing from the worktree. This was a hallucinated investigation result: the controller checked directly with `cat`/`grep` and found both present exactly where expected. A location check that passes is not proof that everything the subagent then reports about file contents is accurate.

The combination that actually worked, for this task and the remaining 7 tasks and 3 review/fix rounds of the session: escalating the implementer model tier from haiku to sonnet, together with keeping the mandatory pwd/branch verification as an explicit, exact-commands-and-exact-expected-values first step with an "abort on mismatch" instruction. With both changes together, zero further incidents of either kind occurred for the rest of the session.

## Guidance

When a dispatch prompt sends a subagent into a specific, already-existing git worktree, treat the working-directory/branch check as a **mandatory instructed first action with named commands and named expected values** — not a prose mention of the target path buried in scene-setting. A subagent that ends up in the wrong checkout of a multi-worktree repo can still find a plausible, self-consistent-looking repo state there (right repo, wrong branch/commit), so nothing about its own experience will look like an error. The check has to be an enforced step, not an ambient fact.

Template block to paste into the top of every such dispatch prompt, filled in per task:

```
MANDATORY FIRST STEP — do this before reading or editing any file:

1. Run these three commands and record their exact output:
     pwd
     git rev-parse --show-toplevel
     git branch --show-current

2. Compare against the expected values for this task:
     expected toplevel : /path/to/.claude/worktrees/<slug>
     expected branch    : <branch-name>

3. Decision rule (no exceptions):
   - All values match  -> proceed with the task normally.
   - ANY value differs  -> STOP immediately. Do not edit, do not commit,
     do not "fix it and continue." Report status NEEDS_CONTEXT with the
     actual pwd/toplevel/branch you observed vs. what was expected, and
     do nothing else.

Do not skip this step because the task "seems simple." Do not infer the
correct directory from context — run the commands and compare literal
strings.
```

Pair this with two more things this session's evidence points to directly:

- **Use a capable model for implementer subagents that will run git commands with side effects (commits, file writes) in a shared multi-worktree repo**, not just for tasks that look intellectually hard. The cheap-model attempt failed twice in a row — once by skipping the verification entirely, once by passing verification and then fabricating an unrelated investigation result (a claim that existing code was missing). Escalating model tier was the change that stopped both failure modes, not just the first one.
- **Never treat a subagent's own completion report as sufficient evidence that work landed correctly.** After each dispatch, the controller should independently re-run the same class of check the subagent was told to run (`pwd`, `git rev-parse --show-toplevel`, `git branch --show-current`) plus a diff/log check of what actually changed, rather than trusting a DONE or DONE_WITH_CONCERNS status at face value. In this session, that independent re-verification — not anything in either subagent's self-report — is what caught both incidents.

On the tooling side: as of this session, the general-purpose Agent-dispatch tool available here (`Agent`) takes `description`, `prompt`, `subagent_type`, an optional `model` override, `run_in_background`, and an optional `isolation` parameter — but no parameter that pins a dispatched agent's working directory to an arbitrary, already-existing path such as a specific pre-created worktree. The only isolation option, `isolation: "worktree"`, *creates a new, temporary* git worktree for the agent to work in; it is not a mechanism for directing an agent into a worktree that already exists (such as the one the outer session is itself running in). So for the "dispatch into an existing, specific worktree" case this incident describes, there is currently no structural/hard constraint available — the mandatory-first-step verification described above is a prompt-level mitigation, not a tool-enforced one. Treat this as accurate for the tooling available in this environment at the time of writing; re-check the tool's current schema before assuming it still holds, in case a future version adds a working-directory-pinning option.

## Why This Matters

A subagent that silently operates in the wrong checkout of a shared, multi-worktree repository is a specifically dangerous failure mode, not just a wasted task: the primary checkout in this incident was itself a live shared workspace with someone else's uncommitted, in-progress work sitting in it. A less careful recovery (`git reset --hard` instead of `git revert --no-commit` on the specific bad commit) would have destroyed that unrelated work as collateral damage, turning a wrong-directory mistake into cross-session data loss. The fact that it didn't happen here was because the recovery method was chosen carefully, not because the risk wasn't real.

The failure is also hard to detect by design: a subagent lost in the wrong branch of the right repo doesn't hit an obvious error. It finds real files, a real git history, and — if a prerequisite feature is simply missing on that branch — a plausible-looking gap that invites the subagent to "fix" it by inventing code, which then reads like ordinary, if slightly out-of-scope, task completion in the subagent's own report. Skimming that report will not surface the problem; only checking the actual repo state does.

Finally, the two-attempt experience here shows that directory verification and content-accuracy are separate risks that need separate mitigations. A subagent can pass a directory/branch check and still be wrong about what's actually in the files it's looking at. Fixing only one of the two failure modes (e.g., adding the pwd check without also raising model capability) would have left the workflow only partially protected.

## When to Apply

- Dispatching implementer subagents (e.g. via a general-purpose Agent-dispatch tool) into a specific, pre-existing git worktree, in a repo that has multiple concurrent worktrees sharing one git object database with the primary checkout.
- Using a cheap/fast model tier (e.g. haiku) for subagent dispatch on any task that will run git commands with side effects — especially `git commit` — in a specific working directory.
- Any dispatch prompt that currently conveys the required working directory only as prose scene-setting ("Work from: `<path>`") rather than as an instructed, mandatory, self-verified first action with exact commands and exact expected values.
- Any point where the controller is tempted to accept a subagent's own DONE/status report as sufficient evidence of correct repo state, instead of independently re-running verification commands itself.
- Any workflow where a subagent's own vantage point could look self-consistent even when it is actually in the wrong location — e.g., a stale branch missing a prerequisite feature that looks, from the subagent's perspective, like "an earlier task just hasn't landed yet" rather than "I am in the wrong checkout."

## Examples

**Before (what failed, attempt 1) — prose-only working-directory mention, cheap model:**

> "Implement task 2 from the plan. Work from: `/Users/.../.claude/worktrees/exciting-cori-f85397`. Follow the existing patterns in the codebase and commit your changes when done."

Result: the subagent operated in the primary checkout instead, committed there, and invented a missing method rather than recognizing it was on the wrong branch.

**Still insufficient alone (attempt 2) — verification added, model tier unchanged:**

> "Before editing, run `pwd` and `git branch --show-current` and confirm you're in the right place. Implement task 2 from the plan..."

Result: verification passed (correct worktree, correct branch), but the subagent then reported `NEEDS_CONTEXT`, incorrectly claiming prerequisite code (`AuthProvider.APPLE`, `User.createAppleUser(...)`) was missing — a hallucinated investigation result the controller had to catch by directly grepping the files itself.

**After (what worked for the remaining 7 tasks) — mandatory checklist-style verification, escalated model tier:**

> Model: sonnet (escalated from haiku for this task and all remaining tasks in the session.)
>
> "MANDATORY FIRST STEP — before reading or editing any file, run `pwd`, `git rev-parse --show-toplevel`, and `git branch --show-current`. Expected toplevel: `/Users/.../.claude/worktrees/exciting-cori-f85397`. Expected branch: `feat/apple-account-revoke`. If any value does not match exactly, stop immediately — do not edit or commit — and report status NEEDS_CONTEXT with what you actually observed. Only if all values match, proceed with: Implement task 2 from the plan..."

Result: zero further wrong-checkout commits and zero further hallucinated-missing-file reports for the rest of the session (7 more implementation tasks plus 3 review/fix rounds, including a final whole-branch review).

**Controller-side habit that caught both incidents:** after each dispatch returns, independently re-run `pwd` / `git rev-parse --show-toplevel` / `git branch --show-current` plus `git log`/`git diff` against the target worktree yourself, rather than accepting the subagent's DONE report as sufficient — this is what actually surfaced both failures in this session, not anything the subagents self-reported.

## Related

- `docs/api-contract.md`, `backend/AGENTS.md` — this repo's confirmed auth direction (unrelated to this incident, but the invented `User.createAppleUser(...)` method touched auth-adjacent code from the `feat/apple-sign-in` branch that the wrong-checkout commit didn't have).
- `superpowers:subagent-driven-development`, `superpowers:using-git-worktrees` — the workflow and worktree-isolation skills in play when this incident occurred.
