---
name: Always update release notes before CI dispatch
description: Before dispatching release.yml, update both whatsNew/whatsnew-en-US and whatsNew/whatsnew-es-419 to reflect the actual changes in the build.
type: feedback
---

Before every CI release dispatch that uploads to a Play Console track, update both `whatsNew/whatsnew-en-US` and `whatsNew/whatsnew-es-419` to describe what's actually in this build.

**Why:** `.github/workflows/release.yml` line 167 wires `whatsNewDirectory: whatsNew` into the `r0adkll/upload-google-play` action, which uploads whatever's in those files as the release notes — verbatim, no regeneration. The files are static, so a stale file means testers (or production users) see notes that don't match the build they're installing. On 2026-05-12 we shipped 2.10.21–2.10.23 to paid closed Alpha testers with notes that still referenced 2.10.20 changes (Spanish ad targeting, banner/dialog fixes); the Layer 2 refund-lag fix — the most tester-relevant change — wasn't called out at all. User left it that time but flagged it as something to remember going forward.

**How to apply:**
- After bumping `versionCode` / `versionName` in `app/build.gradle.kts`, immediately edit both `whatsNew/whatsnew-en-US` and `whatsNew/whatsnew-es-419` to describe the new build.
- Stage all three files in the same commit so the bump and the notes ship together.
- Keep both under **500 chars** — Play Console's API rejects longer (we hit this on v2.10.04 with es-419 at 555 chars, and on v2.10.16 with es-419 at 515 chars; pattern is Spanish runs ~15-25% longer than English and tends to be the one that overruns).
- Lead with the change most relevant to users — pricing/entitlement fixes outrank UI polish, which outranks internal/diagnostic improvements.
- If running `/push` followed by a CI dispatch, the release-notes update is part of "/push" scope when the dispatch will follow. Don't dispatch without the update.
