---
title: React Native modal presentation race condition during login
date: 2026-09-05
category: ui-bugs
module: mobile-auth-flow
problem_type: ui_bug
component: authentication
symptoms:
  - "NicknameEditModal fails to appear on iOS when presented immediately after LoginPromptModal dismisses"
  - "New user signup flow silently skips nickname editing step on iOS"
  - "Race condition occurs when AuthContext batches setIsLoginModalVisible(false) with setUser(...) in same render pass"
root_cause: async_timing
resolution_type: code_fix
severity: medium
related_components:
  - NicknameEditModal
  - LoginPromptModal
  - AuthContext
  - NewUserRedirectWatcher
tags: [react-native-modal, ios-uikit-modal, auth-flow, modal-race-condition, async-timing, signup-flow]
---

# React Native modal presentation race condition during login

## Problem

Presenting `NicknameEditModal` (`mobile/src/components/NicknameEditModal.tsx`) directly off the same `user` state update that closes `LoginPromptModal` (`mobile/src/components/LoginPromptModal.tsx`) risked a silent iOS modal-presentation collision: the second `Modal` could simply never appear, because it was asked to present while the first `Modal`'s native dismissal was still in flight.

## Symptoms

This was caught during whole-branch code review, before the app was ever run against the unfixed code, so there is no observed crash log or console error to cite. If shipped as originally written, the expected symptom would have been:

- After a first-time Apple Sign In, `NicknameEditModal` would silently fail to appear — no error, no warning, nothing in the console or native logs.
- The user would land directly on the Board tab (via the effect's fallback path) having never been prompted to set a nickname, defeating the entire point of the feature (Apple Sign In only supplies a display name on first authorization, so a missed prompt here means the nickname is never captured).
- The failure would be intermittent/timing-dependent rather than 100% reproducible, since it depends on exactly how React batches the state updates and how quickly `LoginPromptModal`'s fade-out dismissal is handed off to iOS — making it the kind of bug that is easy to miss in casual manual testing and hard to reproduce on demand.

## What Didn't Work

N/A — caught during review before an incorrect or incomplete fix was ever attempted. There was no failed-attempt cycle; the risky code path (unconditionally opening `NicknameEditModal` from the `user`-watching effect with no delay) was identified by reasoning about React batching and iOS modal handoff, and replaced directly with the delayed version below.

## Solution

In `mobile/App.tsx`, `NewUserRedirectWatcher`'s effect defers presenting `NicknameEditModal` by 350ms instead of doing so synchronously on the same tick that `user` becomes non-null:

```tsx
useEffect(() => {
  if (user && consumeNewUserRedirect()) {
    // LoginPromptModal이 닫히는 애니메이션과 겹치지 않도록 지연 후 표시한다.
    // 같은 렌더에서 두 Modal이 동시에 열고 닫히면 iOS에서 두 번째 Modal이
    // 조용히 나타나지 않는 경합이 생길 수 있다 (RN 기본 fade 애니메이션 기준 350ms 여유).
    const timer = setTimeout(() => setIsNicknamePromptVisible(true), 350);
    return () => clearTimeout(timer);
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [user]);
```

Everything else in `NewUserRedirectWatcher` — `goToBoard()`, the `handleNicknameConfirm` save handler, and the returned `NicknameEditModal` JSX — is unchanged. The fix only delays *when* the modal is asked to present, not what happens afterward. The cleanup function (`return () => clearTimeout(timer)`) clears the pending timer if the effect re-runs on a new `user` value or if the component unmounts before the 350ms elapses, so the modal cannot pop up after the context it belonged to is gone.

This is fixed on branch `feat/nickname-editing` as currently committed; it has not yet been merged, and there is no PR opened for it yet.

## Why This Works

The root cause is a collision between React's state-update batching and iOS's native modal-presentation handoff:

- In `mobile/src/contexts/AuthContext.tsx`, both login paths call `setUser`, `setAccessToken`, `setPendingNewUserRedirect`, and `setIsLoginModalVisible(false)` together in one function body, which React batches into a single render/commit. `isLoginModalVisible` controls `LoginPromptModal`; setting it to `false` starts that modal's fade-out dismissal in the very same commit where `user` becomes non-null.
- `NewUserRedirectWatcher`'s `useEffect(() => { if (user && consumeNewUserRedirect()) {...} }, [user])` in `mobile/App.tsx` fires off that same `user` change. Without a delay, the effect would call `setIsNicknamePromptVisible(true)` — presenting `NicknameEditModal` — in the same commit or the very next one, while `LoginPromptModal`'s native dismissal (`presentViewController`/`dismiss` handoff on iOS) is still in flight.
- Presenting a second RN `Modal` while iOS is still mid-dismissal of a first one is a known way for the second `presentViewController` call to be silently dropped: no error is thrown, the modal component's `visible` prop is `true`, but nothing appears on screen.
- The fix breaks this collision by waiting 350ms — a safe margin over React Native's default `Modal` `animationType="fade"` duration (~300ms) — before flipping `isNicknamePromptVisible` to `true`, giving `LoginPromptModal` time to fully finish dismissing first.

**Apple vs. Kakao path asymmetry**: the two login paths in `AuthContext.tsx` are not equally at risk, but not for the reason it might first appear (network round-trip timing) — the real difference is *which state transition actually closes `LoginPromptModal`*. `kakaoLogin()` (called synchronously when the user taps the Kakao button on `LoginPromptModal`) sets `setIsLoginModalVisible(false)` immediately, before `setIsKakaoWebViewVisible(true)` opens the Kakao WebView — so `LoginPromptModal` is already closed well before `submitKakaoCode` (invoked later, from inside the WebView's navigation callback) ever runs. By the time `submitKakaoCode`'s final batch calls `setIsLoginModalVisible(false)` again, `isLoginModalVisible` is already `false` — a false→false no-op that triggers no modal transition at all. In `appleLogin`, by contrast, tapping the Apple button calls `appleLogin` directly while `LoginPromptModal` is still open (`isLoginModalVisible` still `true`), and `isLoginModalVisible` is only flipped to `false` in the same final batch as `setUser` — a genuine true→false transition co-batched with `user` becoming non-null, which is exactly the collision this fix addresses. This matters because Apple Sign In is precisely the login method this nickname-editing feature was built for (Apple doesn't reliably supply a display name, unlike Kakao), so the one path with a real modal-close-and-open collision is also the one the feature depends on most.

## Prevention

- When a login/auth flow that dismisses one `Modal` needs to present another `Modal`-based UI on success, don't chain the second modal's `visible=true` directly off the same state update that closes the first. Defer it — either with a short delay past the closing modal's animation duration (as done here), or via an `onDismiss` callback if the modal component in use supports one — so the two presentations don't overlap on iOS.
- Treat this as a general RN pattern, not an Apple-specific one: any state-batched transition that both closes an existing `Modal` and (via a `useEffect` on the same changed value) opens another one is at risk. Check whether the closing modal's `visible`/`isXVisible` flag was already flipped to `false` by an earlier, unrelated interaction (as it incidentally was for Kakao, via `kakaoLogin()` closing `LoginPromptModal` on tap, well before the async flow completes) before assuming a later re-set of that same flag is a real transition rather than a no-op.
- **Consume-then-delay gotcha**: `consumeNewUserRedirect()` flips the pending-redirect flag to `false` at the moment the effect runs, before the 350ms timer fires — the flag is consumed immediately, but the visible effect (showing the modal) is delayed. If `user` were somehow set again within that 350ms window, the effect would re-run, its cleanup would clear the pending timer, and the nickname prompt for that signup would be silently lost rather than delayed, since the flag has already been consumed and won't be true again. In the current codebase this isn't a live bug (`user` doesn't change again within 350ms of a fresh login, and the project doesn't use `React.StrictMode`, which double-invokes effects in development and could otherwise surface exactly this race). Treat "consume immediately, act after a delay" as a fragile pattern if reused elsewhere with different timing — prefer consuming the flag only when the delayed action actually fires, or guarding against a re-entrant `user` change within the delay window.
- This fix was applied proactively based on review reasoning (React batching semantics + known iOS `presentViewController` handoff behavior), not from an observed failure — it has not yet been confirmed via manual iOS Simulator testing. That manual confirmation remains outstanding and should be done before treating this class of timing issue as fully closed.

## Related Issues

None found in `docs/solutions/` or GitHub issues at time of writing.
