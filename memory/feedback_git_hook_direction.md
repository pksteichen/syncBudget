---
name: Git hook is one-way (repo → Download)
description: The post-commit hook copies FROM the repo TO Download/BudgeTrak Dev Project Files. It is NOT bidirectional. Files placed in the Download copy are wiped on the next commit. Always place new files in the repo first.
type: feedback
---

The post-commit hook in `.git/hooks/post-commit` runs `git archive HEAD | tar -x` which exports the repo to Download. It is one-directional:

- Repo → Download: automatic on every commit
- Download → Repo: does NOT happen

**Why:** `git archive` replaces the entire Download folder with the repo contents. Any files added directly to the Download copy (not in git) are deleted.

**How to apply:** When the user creates or places files, always put them in the repo directory first, then commit. Warn if the user mentions adding files to the Download copy — they'll be lost on the next commit.
