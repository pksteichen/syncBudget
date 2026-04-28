---
name: Tech Advantage Pages repo location
description: GitHub Pages site at techadvantagesupport.github.io serves the BudgeTrak privacy policy + homepage. Replaced the older budgetrak-legal repo on 2026-04-27.
type: reference
---
The Tech Advantage / BudgeTrak public-facing web content lives in its own repo, separate from the app code.

**Git remote**: https://github.com/techadvantagesupport/techadvantagesupport.github.io
**Primary branch**: `main` (no `dev` branch — small repo, commits go straight to main)
**Live URL**: https://techadvantagesupport.github.io/

**Local working copy**: `/storage/emulated/0/Download/Tech Advantage Pages`

On Android external storage so the user can view files from the Files app. Termux flagged it as "dubious ownership" on first access — already configured via:
```
git config --global --add safe.directory '/storage/emulated/0/Download/Tech Advantage Pages'
```

**Local commit attribution**: configured per-repo to `Tech Advantage LLC <support@techadvantageapps.com>` via repo-local `git config user.name/user.email` (NOT global). The dailyBudget repo and budgetrak-legal repo continue to use `pksteichen <pksteichen@users.noreply.github.com>`.

**Current files**:
- `index.md` — Pages homepage (mirrors README.md content; needed because GitHub Pages serves `index.md` at the root URL).
- `README.md` — visible on github.com when viewing the repo.
- `privacy.md` — privacy policy, served at https://techadvantagesupport.github.io/privacy. Includes the "AI-Assisted Features" section (receipt OCR + CSV categorization).
- `budgetrak_logo.png`, `ic_app_icon.png` — branding assets used by the Play Store listing.

**When to touch this repo**:
- Any new user-visible data practice (new analytics, new AI feature, new third-party integration) needs a matching privacy.md update before the build ships.
- Branding changes (logo, name) also live here.
- Homepage content changes (e.g. when adding a second product beyond BudgeTrak).

**How to access**:
```
cd "/storage/emulated/0/Download/Tech Advantage Pages"
git status
git pull origin main
# edit privacy.md or index.md ...
git add <file>
git commit -m "privacy: ..."
git push origin main
```

**In-app references**: as of 2026-04-27, the privacy policy URL is NOT hardcoded anywhere in the app source (verified by grep). It only appears in the Play Store listing field, which is set when the app is published.

## Legacy repo (kept alive for redirect)

The previous repo at https://github.com/techadvantagesupport/budgetrak-legal is still live, serving the same content under the older URL `techadvantagesupport.github.io/budgetrak-legal/privacy`. **Do NOT delete this repo until v2.7 is fully retired in production** (any external link or older build that still points at the old URL relies on the legacy Pages site continuing to serve). Local clone at `/storage/emulated/0/Download/BudgeTrak Legal Files`.

After v2.7 is no longer in active distribution, options are:
1. Leave the legacy repo in place (cheap, harmless).
2. Replace its `privacy.md` with a one-line `<meta http-equiv="refresh">` redirect to the new URL.
3. Archive or delete (only after verifying no live users on v2.7).
