---
name: Don't delete google*.html in the Pages repo — it's site-verification for Play Console / Search Console
description: The file google17d827ef1b64ae6d.html at the root of the techadvantagesupport.github.io Pages repo is a Google site-verification token tying the techadvantagesupport.github.io domain to the BudgeTrak developer account. Removing or renaming it breaks ownership verification and can cause Play Console + Search Console + Google Payments to flag the website as no longer owned.
type: feedback
---

**Rule.** The file `google17d827ef1b64ae6d.html` (or any future `google*.html` Google places at the Pages repo root) is a verification token, not a stray file. It must stay live at `https://techadvantagesupport.github.io/google17d827ef1b64ae6d.html`. Don't delete, rename, or move it during cleanup passes.

**Why:** Google verifies website ownership by HTTP-fetching this exact URL and checking the contents match a server-side record. The single line in the file is `google-site-verification: google17d827ef1b64ae6d.html`. If the URL 404s or returns different content, Google considers the website no longer owned by us, which:
- Removes "techadvantagesupport.github.io" from the verified properties on the Google account
- Can trigger Play Console / Google Payments warnings about the developer's website link being unverified
- Forces a full re-verification + a new token name (Google generates a fresh per-attempt token)

**Where:**
- Lives in the Pages repo: `/storage/emulated/0/Download/Tech Advantage Pages/google17d827ef1b64ae6d.html`
- Pushed to: `https://github.com/techadvantagesupport/techadvantagesupport.github.io` (main branch, root)
- Served at: `https://techadvantagesupport.github.io/google17d827ef1b64ae6d.html`

**How to apply:**
- If running a "clean stray files" pass on the Pages repo, treat any `google*.html` at the root as load-bearing and skip.
- If Google ever issues a new verification (e.g., for a new property like Search Console or Ad Manager), it'll be a separate `google<hash>.html` file — keep both/all of them.
- If the file accidentally gets deleted or moved, restore from git history and re-push to main.

**Added 2026-05-08** during the org-account verification flow tied to converting the Play Console account from Personal → Organization.
