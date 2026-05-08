---
name: Release pipeline — /push must end with `gh workflow run release.yml`
description: BudgeTrak's CI is workflow_dispatch only. The release pipeline (push → CI → Play Console internal draft) is a routine, proven path used 5+ times. Always dispatch CI as part of /push — don't wait to be asked.
type: feedback
---

**Rule.** Every `/push` to dev for a version bump or shippable change MUST end with `gh workflow run release.yml --ref dev`. Don't wait for the user to remind you. Don't treat it as new — this pipeline has run successfully 5+ times.

**Why:** the workflow is `on: workflow_dispatch:` only (no push or tag trigger). The header comment in `.github/workflows/release.yml` calls it "manual-dispatch only for now" but the user has been running this exact pipeline routinely. Forgetting means the user has to remind me, which has happened repeatedly.

**How to apply:**

After `/push` succeeds, run:
```
gh workflow run release.yml --ref dev
```

Default inputs (set 2026-05-08, commit cd55f78):
- `publish_to_play=true` — auto-uploads AAB to Play Console
- `release_track=internal` — lands on internal testing track
- `release_status=draft` — user promotes with one click in Console

So a bare `gh workflow run release.yml --ref dev` does the full pipeline: build → sign → upload AAB to Play Console internal as draft. ~8 min total.

Track via:
```
gh run list --limit 3 --branch dev --workflow release.yml
gh run view <id> --json status,conclusion,jobs
```

**When to override defaults:**
- Closed/alpha/beta/production: pass `-f release_track=closed` etc. (don't auto-promote production without user confirmation)
- Build-only (no upload): pass `-f publish_to_play=false`

**Don't** assume push triggers CI. **Don't** ask the user "should I dispatch CI?" — just do it as the final step of /push.

**Fail mode to watch for:** if the run logs show "publish_to_play was true but PLAY_SERVICE_ACCOUNT_JSON secret is not set; skipping Play upload", the secret needs to be added to the GitHub repo's Actions secrets. That's a one-time setup; tell the user.
