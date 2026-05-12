# BudgeTrak

Android budgeting app by **Tech Advantage LLC** — package `com.techadvantage.budgetrak`.

## Where to find things

### Current state / todos
- **`memory/`** — single source of truth for project context. Read `memory/MEMORY.md` first; it indexes every memory file (specs, project notes, feedback rules, references).
- **`memory/project_prelaunch_todo.md`** — the active todo list. _(Replaces the old `Todo.txt` at repo root, deleted 2026-04-13.)_

### Docs
- **`docs/BudgeTrak_SSD_v2.8.md`** — system specification.
- **`docs/BudgeTrak_LLD_v2.8.md`** — low-level design.
- **`docs/RECEIPT_PHOTO_DESIGN.md`** — receipt photo design notes (folded into `memory/spec_receipt_photos.md`; kept here as historical).

### Code
- **`app/src/main/java/com/techadvantage/budgetrak/`** — Android Kotlin source. 47 k lines / 94 files.
- **`app/src/main/AndroidManifest.xml`** — declares only `INTERNET`; camera/media go through runtime request + photo picker.

### Backend
- **`functions/`** — Firebase Cloud Functions (Node.js 22). Deployed from this dir via `firebase deploy --only functions`.
- **`firebase.json`** / **`.firebaserc`** — Firebase project config (project ID: `sync-23ce9`).
- **`firebase-config-reference.txt`** — full settings snapshot (security rules, App Check, TTLs, Cloud Functions, BigQuery). Keep this in sync with Firebase Console changes.

### Tools
- **`tools/query-crashlytics.js`** — Node BigQuery query tool for Crashlytics export. See `memory/reference_crashlytics_bigquery.md` for usage.

## Build (Termux)

```
export JAVA_HOME=/data/data/com.termux/files/usr
./gradlew assembleDebug --no-daemon
```

APK ends up at `app/build/outputs/apk/debug/app-debug.apk`. Copy to `/storage/emulated/0/Download/` to install.

## Memory system

Claude's auto-memory lives at `~/.claude/projects/-data-data-com-termux-files-home/memory/`, which is a **symlink** to this repo's `memory/` directory. Every memory write lands in the tracked working tree, shows up in `git status`, and gets committed + pushed via `/push`. Cloning the repo on a new device and re-creating the symlink restores all memory.

On a fresh device:
```
ln -s /path/to/dailyBudget/memory ~/.claude/projects/-data-data-com-termux-files-home/memory
```

A sibling directory `~/.claude/projects/-data-data-com-termux-files-home/private-notes/` stays **un-tracked** — it's the destination for genuinely personal memory (health notes, financial info outside BudgeTrak, etc.) that shouldn't end up on GitHub. See `memory/feedback_memory_routing.md` for the routing rule.

## Branches

`dev` (working) → `main` (releases). Default push: `dev` only.
