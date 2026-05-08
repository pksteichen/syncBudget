---
name: Update whatsNew/* before each user-facing release
description: CI's release.yml uploads whatsNew/whatsnew-en-US and whatsnew-es-419 verbatim with every Play upload via the r0adkll/upload-google-play action's whatsNewDirectory parameter. If those files aren't updated, every Play release shows the prior version's notes — confused testers.
type: feedback
---

**Rule.** Before bumping versionCode for a user-facing release, update both:
- `whatsNew/whatsnew-en-US`
- `whatsNew/whatsnew-es-419`

with concise release notes describing what's new since the last update users saw. CI uploads them verbatim; they appear on the Play Store "What's new" section.

**Constraints:**
- **Hard cap: 500 characters per file** (Play API rejects above that). The action also rejects empty files. Aim ~300-450 chars to leave headroom.
- Avoid internal jargon (`RE`, `BackgroundSyncWorker`, etc.) — these go to end users.
- Bullet points (• character) render nicely in Play's UI; line breaks are honored.
- Spanish file is `es-419` (LATAM variant), not `es-ES` — confirmed in Spanish strings audit.

**When to update:**
- Any user-visible feature/fix going to a track real testers see (Internal/Closed/Open/Production).
- Skip for CI-only / workflow / build-system changes if no app behavior changed — but still consider whether *cumulative* notes since last update need refreshing (testers may not have seen the prior notes either).

**Confirmed gap (2026-05-08):** files were last updated in v2.10.05; every release v2.10.06 through v2.10.13 went out with stale "v2.10.05" notes because nobody refreshed the files. Fixed in v2.10.14 with a cumulative summary covering everything since v2.10.05.

**How to apply:**
- Edit the two files alongside the versionCode bump in `app/build.gradle.kts`.
- Same commit, same push, same CI dispatch.
- Don't forget the Spanish file — it's always two updates.
