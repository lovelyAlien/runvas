# Concepts

Shared domain vocabulary for this project — entities, named processes, and status concepts with project-specific meaning. Seeded with core domain vocabulary, then accretes as ce-compound and ce-compound-refresh process learnings; direct edits are fine. Glossary only, not a spec or catch-all.

## Content Moderation

### Report
A flag raised against a specific piece of user-generated content (a post, a comment, or a course comment), recording who flagged it, why, and what happens to it. A Report always targets exactly one piece of content, but the same content can accumulate multiple Reports from different reporters.

Lifecycle: Pending, then either Resolved (the reported content was removed, and — when the reviewing operator chose to — the author was also Banned) or Dismissed (the report was reviewed and no action was taken). Resolving one Report against a piece of content also resolves every other Pending Report against that same content, since the content itself is gone.

### Ban
A moderator action that permanently marks a user's account as no longer allowed to use the service, applied to the author of removed content. A Ban blocks all future sign-in for that account and — from the moment it is applied — also invalidates any session the user already holds, so the effect is immediate rather than waiting for the user's next login. There is currently no way to reverse a Ban.
