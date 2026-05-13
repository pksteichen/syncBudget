---
name: Release pipeline — CI dispatches from main, dev is for development
description: BudgeTrak's CI is workflow_dispatch only. As of 2026-05-13 (post-v2.10.27), tracks-bound dispatches go from main only; dev is the integration branch for ongoing work. /push pushes to dev as usual but does NOT auto-dispatch CI — promotion to main is a deliberate step.
type: feedback
---

**Branch roles (refined 2026-05-13):**
- **`main`** — release source. Only branch CI dispatches from.
- **`dev`** — bug-fix testing / staging branch. Work merges here from feature branches when ready for integration verification; bug fixes land here first to soak before promotion to main.
- **`feature/*`** — active feature development (themes, ad polish, etc.). Branches off dev. Merge back to dev when the feature is integration-ready.

**Rule.** Tracks-bound CI dispatches use `--ref main` only. Feature work happens on `feature/*` branches; integration testing happens on `dev`; releases come from `main`. The user will explicitly ask to promote dev → main (fast-forward merge) and dispatch CI from main when ready to ship to internal/alpha.

**Why this changed (2026-05-13):** through v2.10.27 the pipeline was `/push to dev → dispatch from dev`. After v2.10.27 stabilized the ad layer, the user moved to a `main = release source` model so all production-bound builds come from a vetted branch. Dev becomes the bug-fix soak zone before promotion. Feature branches keep parallel work isolated. This holds until production launch is complete.

**Why:** the workflow is `on: workflow_dispatch:` only (no push or tag trigger). The header comment in `.github/workflows/release.yml` calls it "manual-dispatch only for now" but the user has been running this exact pipeline routinely. Forgetting means the user has to remind me, which has happened repeatedly.

**How to apply:**

After `/push` succeeds, run:
```
gh workflow run release.yml --ref main
```

Default inputs (set 2026-05-08, commits cd55f78 + 7225a72 + 2075a08):
- `publish_to_play=true` — auto-uploads AAB to Play Console
- `release_track="internal,alpha"` — comma-separated, publishes to BOTH internal and closed/alpha tracks in one upload (action's `tracks:` plural parameter)
- `release_status=completed` — submits for Play's automatic review and goes live; ~5 min for internal, 1-2 h for alpha

So a bare `gh workflow run release.yml --ref main` does the full pipeline: build → sign → upload AAB once to Play Console for both internal and alpha tracks → auto-publish. ~8 min CI + ~5 min Play review = ~13 min from `gh workflow run` to the build being live for Internal testers. No Console clicks.

**Why `tracks:` plural matters:** dispatching twice with different `track:` values fails the second time with "Version code N has already been used" because each dispatch uploads independently. Plural-tracks publishes once to many destinations.

Track via:
```
gh run list --limit 3 --branch main --workflow release.yml
gh run view <id> --json status,conclusion,jobs
```

**When to override defaults:**
- Single track: pass `-f release_track=internal` or `=alpha` or `=production` etc.
- Custom combo: pass `-f release_track=internal,alpha,beta` (any combination, comma-separated). Note: Play Console's "Closed testing" track is `alpha` in API terms; "Open testing" is `beta`.
- Build-only (no upload): pass `-f publish_to_play=false`
- Manual-promote (don't auto-publish): pass `-f release_status=draft`. Useful for risky releases you want to inspect in Console before rollout.
- Halted (uploaded but immediately paused): pass `-f release_status=halted`.
- Don't auto-promote to production without explicit user confirmation.

**Don't** assume push triggers CI. **Don't** auto-dispatch CI on `/push` anymore — that triggered from-dev releases under the prior workflow. Now: `/push` ends at the dev push; wait for the user to explicitly promote dev → main and ask to dispatch.

**Fail mode to watch for:** if the run logs show "publish_to_play was true but PLAY_SERVICE_ACCOUNT_JSON secret is not set; skipping Play upload", the secret needs to be added to the GitHub repo's Actions secrets. That's a one-time setup; tell the user.
