# BudgeTrak OCR Harness

Offline Node.js harness for iterating on the receipt-extraction prompt and A/B-testing Gemini vs Anthropic before porting the winner to the Android app.

## Why this exists

In-app iteration is slow: each prompt tweak needs an APK rebuild, reinstall, and a manual share-gesture per receipt. This harness runs the extractor against a local image set in seconds, isolates the extractor from UI/Compose/ReceiptManager, and makes A/B side-by-side trivial. See `memory/project_ocr_receipt_capture.md` → "Prompt iteration strategy" for context.

## Setup

```
cd tools/ocr-harness
npm install
cp .env.example .env
# Edit .env and add GEMINI_API_KEY (required) and optionally ANTHROPIC_API_KEY
```

**Gemini key:** generate at <https://aistudio.google.com/app/apikey>. Your Google account must be on a paid (Blaze-linked) project for the no-training/no-retention guarantee to apply. Free-tier Spark keys *will* train on your data — don't use those here.

**Anthropic key (optional):** generate at <https://console.anthropic.com/settings/keys>. Separate from your claude.ai subscription — you'll need to add pay-as-you-go billing to the API console. Running the full test set costs well under $1.

## Adding test data

1. Drop receipt images into `test-data/images/` (JPEG, PNG, or WebP).
2. Add an entry to `test-data/labels.json` for each:

```json
{
  "file": "sroie_001.jpg",
  "source": "SROIE",
  "difficulty": "easy",
  "merchant": "Starbucks",
  "date": "2026-03-14",
  "amount": 5.47,
  "categoryId": 4
}
```

- `source`: "SROIE" | "CORD" | "internal-screenshots" | etc. — used for per-segment accuracy later.
- `difficulty`: "easy" | "medium" | "hard" — your call, based on how legible the receipt is.
- `categoryId`: optional; omit if you don't want to grade categorization. IDs are defined in `src/categories.js`.

### Where to get starter images

- **SROIE** (1000 English receipts): <https://huggingface.co/datasets/priyank-m/SROIE_2019_text_recognition>
- **CORD** (Indonesian, good for multi-category tests): <https://github.com/clovaai/cord>
- **Your own screenshots**: share-intent data from DoorDash, Uber Eats, Amazon, bank notifications, etc. No public dataset covers these.

## Running

```
npm start                    # Gemini only (default)
npm run gemini               # same
npm run anthropic            # Anthropic only
npm run both                 # side-by-side
node src/index.js --limit 5  # first 5 test items
node src/index.js --verbose  # dump each raw response
```

Output:

```
━━━ GEMINI (prompt v0.1) ━━━
  ✓✓✓✓ sroie_001.jpg                    m=Starbucks  d=2026-03-14  $=5.47  (1240ms)
  ✓✓✗· sroie_002.jpg                    m=Walmart  d=2026-02-10  $=42.10  (980ms)
  ...

  Summary  gemini
    merchant  95.0%  (19/20)
    date      85.0%  (17/20)
    amount    100.0%  (20/20)
    category  75.0%  (15/20)
    avg latency  1050ms
    saved → results/gemini-v0.1-2026-04-14T19-42-31-000Z.json
```

## Iterating on the prompt

Edit `src/prompt.js`, bump `PROMPT_VERSION`, re-run. Each run dumps to `results/` stamped with provider + version + timestamp so you can diff.

When per-field accuracy is good enough:

1. Lock in `PROMPT_VERSION`.
2. Port the prompt text to `MainViewModel` or a new `ReceiptExtractionService.kt`.
3. Put it in Firebase Remote Config (key: `ocr_receipt_prompt_v{N}`) so production prompts can change without an app release.

## Files

- `src/index.js` — runner + CLI
- `src/prompt.js` — prompt template (edit this to iterate)
- `src/schema.js` — responseSchema (for both Gemini structured-output and manual validation)
- `src/categories.js` — simulated user category list
- `src/providers/gemini.js` — Gemini 2.5 Flash via `@google/genai`
- `src/providers/anthropic.js` — Claude Haiku 4.5 via `@anthropic-ai/sdk`
- `src/grader.js` — per-field grading rules

## Not in scope for v0

- Schema-gap re-ask (add after we see baseline accuracy)
- Confidence-gated re-ask
- Remote-Config prompt loading (production concern, not harness)
- Full Firebase AI Logic client path (Android-only SDK; harness uses Gemini Developer API directly — same prompt + schema, same model, same account)
