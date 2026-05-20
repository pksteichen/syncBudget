---
name: Help Chat assistant — shipped to dev 2026-05-20
description: AI-powered in-app help chat grounded in a comprehensive user-focused knowledge base, with daily caps, off-topic refusal, email escape, sentiment scoring, and a 2-day-debounced Play Store review prompt on sentiment ≥ 9. Merged into dev as commit 59d6601 (Merge feature/help-chat). The feature/help-chat branch has been deleted. Remaining follow-up: cost-ceiling Cloud Function (server-side circuit breaker) + on-device manual QA on dev before next release dispatch.
type: project
originSessionId: ca028513-626b-45e8-ad1c-a1863966bd91
---
# Help Chat assistant — progress + TODO

**Status:** Merged into `dev` on 2026-05-20 as commit `59d6601`. The `feature/help-chat` branch has been deleted locally and on origin. Source-of-truth from this point is `dev`.

**Significant commits in the merge** (oldest → newest, branch history preserved via `--no-ff`):
- `473d746` UI shell + per-device consent flow + Firestore upload pipeline (anonymous 7-day TTL)
- `885fffa` comprehensive user-focused KB (~75 KB / 1744 lines), synthesized from 5 parallel Explore-agent cluster sweeps
- `7493a96` / `d5d3a09` / `9418a41` drift fixes (memory spec / LLD / SSD) for BI_WEEKLY hidden from new-entry dropdowns + SECURESYNC_CSV display name
- `2f44e40` Gemini Send wiring + daily caps + email escape (`support@techadvantageapps.com`)
- `5136a6d` HelpChatPromptBuilder v2 — implicit-cache-friendly preamble+suffix assembly, locale interpolation dropped
- `cfe5e3e` per-call usage telemetry (`ai_call_metrics` Analytics event across all 3 Gemini callers)
- `7f97783` + `8ed01c3` + `4641018` sentiment scoring (1-10) + tappable Play Store review prompt (sentiment ≥ 9, 2-day debounce) + KB feedback-channel rewording
- `f65d1d4` Dashboard Help intro section ("Chat With Our Helper") above the Solari section with the up-right arrow visual
- `e4e7492` shared HelpChatHost — chatbot icon now on every help page's top app bar
- `1b7fcb5` + `c6c14a1` vendor-neutral in-app copy (consent, review prompt, AI CSV help)
- `6151734` GDPR Article 30 Record of Processing Activities (`docs/BudgeTrak_GDPR_RoPA_v1.md`)

## Goal
An in-app help chat that answers configuration / budgeting / how-to / error-message questions, grounded on a hand-curated knowledge base. Reduces support email volume; provides 24/7 instant guidance.

**Why:** Users hit common questions (period configuration, sync setup, "why is my budget negative", AI quirks). Email round-trips are slow and don't scale. A scoped LLM with a good knowledge base can deflect most.

---

## ✅ Shipped this session (2026-05-19)

### UI shell
- **Chatbot icon** in Dashboard Help app bar (`ic_chatbot.png`, tintable black-on-alpha).
- **`HelpChatDialog`** — AdAware, fills available vertical space, scrollable history + PulsingScrollArrows + 3-line pinned input + footer with Email / Exit / Clear / Send. Send currently appends a placeholder bot reply for UI testing.
- **`HelpChatConsentDialog`** — title, scrollable body, underlined "View Privacy Policy" link (opens `ACTION_VIEW`), Cancel + Accept. Accept persists consent + opens chat; Cancel returns to help page.

### Local persistence
- **`HelpChatStore`** (`data/HelpChatStore.kt`) — JSON file at `filesDir/help_chat.json`. State: `messages`, `chatId` (UUID v4, generated on first message), `lastUploadAt`, `lastUploadedCount`. Load on dialog open prunes messages > 48 h old.
- **`HelpChatMessage`** data class — timestamp, fromUser, text.

### Server upload
- **`HelpChatUploader`** (`data/HelpChatUploader.kt`) — writes to Firestore `helpChatLogs/{chatId}` via `SetOptions.merge()`. Two entry points:
  - `uploadIfStale(ctx)` — 5-min debounce + dirty-gated, called on dialog dismiss.
  - `uploadNow(ctx, chatId, messages)` — explicit, called by Clear with snapshot capture before local wipe.
- Anonymous Firebase Auth sign-in inline for solo users (no SYNC).
- Try/catch + 15 s timeout + DEBUG-only `Log.w` on failure.
- Doc payload includes `expireAt = now + 7 days` as the TTL trigger field.

### Settings + consent persistence
- **`vm.helpChatConsent`** Boolean pref (`helpChatConsent`, default false).
- **Settings → Privacy** checkbox: "Allow Chatbot to transmit and store your messages…" with descriptive subtitle. Default unchecked on install; auto-checks via the consent dialog; unchecking revokes consent so the dialog re-appears on next chat open.

### Backend wiring
- **Anonymous Auth provider** enabled on `sync-23ce9` (Firebase Identity Toolkit API).
- **`firestore.rules`** deployed (ruleset `f9ffebde-4584-479c-9f55-db712b73a8e5`) — `helpChatLogs/{chatId}` allows `get/create/update` if authenticated, `list/delete` forbidden. 128-bit UUID doc IDs prevent enumeration.
- **Firestore TTL policy** configured on `helpChatLogs.expireAt`, status = Serving.
- Placeholder doc `helpChatLogs/ttl_bootstrap_placeholder` written with past `expireAt` to surface the collection group in the TTL picker; will self-delete on next sweep.

### Disclosure
- **Privacy policy** updated at `techadvantagesupport.github.io/privacy` (EN + ES) — new "Help Chat Assistant" subsection under AI-Assisted Features, Gemini row in third-party table expanded, Authentication section notes anonymous sign-in for solo users, Your Rights bullet added for revoking consent, Data Retention adds 7-day server + 48-hour local entries.
- **Play Console Data Safety form** updated — added Messages → Other in-app messages (Optional, App functionality + Fraud-prevention + Analytics); User IDs description expanded to cover anonymous sign-in triggered by Help Chat consent. Submitted for review 2026-05-19.

### Strings + translation context
- New `HelpChatStrings` data class added to all four strings files (AppStrings/EnglishStrings/SpanishStrings/TranslationContext). Covers icon descr, chat UI, consent dialog body+link+buttons.
- New `SettingsStrings.helpChatConsent` + `helpChatConsentDesc` for the Privacy-section checkbox.

---

## ✅ Shipped 2026-05-19 evening (commit `2f44e40`)

### Gemini integration
- `data/ai/HelpChatGeminiService.kt` — raw HTTP via existing `GeminiHttpClient`, mirrors `AiCategorizerService` pattern. Model `gemini-2.5-flash-lite`, temperature 0.2, JSON schema `{ reply: string }`. Transient retry (429/5xx, network), 30 s timeout, Crashlytics on terminal failure.
- `data/ai/HelpChatPromptBuilder.kt` — system prompt with strict on-topic scoping, off-topic refusal + email-escape pointer, language matching (`Locale.getDefault().toLanguageTag()` hint + latest-user-message signal), 10-turn history window, KB injected verbatim between `<<<KB ... KB>>>` markers.
- Send wiring in `HelpChatDialog`: append user msg → "Thinking…" bot row → call service → append real reply (or `errorReply` on failure) → bump daily count (success only) → `uploadIfStale`. Send disabled while in-flight.

### Daily-limit logic
- `HelpChatStore` gained `dailyCount` + `dailyResetEpochDay` (`LocalDate.now().toEpochDay()`), persisted in the same `help_chat.json`. Caps: **Free 10 / Paid 25 / Subscriber 50** as `DAILY_CAP_*` constants.
- `getDailyCap(context)` reads `isPaidUser`/`isSubscriber` directly from `app_prefs` (no VM plumbing). `canSendToday` / `remainingToday` / `incrementDailyCount` rotate the counter at local midnight.
- Counter survives Clear (no bypass via reset) and survives the 48 h message prune (no free reply by waiting).
- Cap-reached UX: input disabled, placeholder swaps to `dailyLimitHint` ("…try tomorrow, or tap Email. Paid users and Subscribers get more chats per day"), Send greyed out.

### Email escape hatch (replaces toast stub)
- `launchEmail` private helper in `HelpChatDialog.kt` opens `ACTION_SENDTO mailto:support@techadvantageapps.com` (NOTE: `techadvantageapps.com`, NOT the global `techadvantagesupport@gmail.com` — this is the Help Chat-specific support address) with prefilled subject (`emailSubject` string) and body (`emailBodyIntro` + transcript + `[chat-id: <uuid>]` footer for Firestore cross-reference).
- Body capped at 3500 chars; longer chats append `[…transcript truncated…]` and rely on support pulling the rest from the 7-day Firestore log.

### Comprehensive knowledge base + cache-friendly prompt assembly
- `app/src/main/assets/help_chat_kb.md` — ~75 KB (1744 lines, ~21 K input tokens) replacing the original stub on 2026-05-19 (commit `885fffa`).
- `HelpChatPromptBuilder.kt` v2 emits an explicit `preamble + suffix` assembly so Google's implicit prompt cache fires. Preamble (system prompt + 8 rules + KB) is byte-identical across all devices and turns; the variable per-turn content (history + latest user message) goes in the suffix. Locale interpolation was dropped (it fragmented the cache per-device); the system prompt's rule 5 now relies on matching the user's message language. Full rationale + future explicit-caching plan: [`feedback_gemini_prompt_caching.md`](feedback_gemini_prompt_caching.md).
- **Per-call cost monitoring shipped 2026-05-20**: `GeminiHttpClient.generate` now takes a `usageCallback` parameter; all three Gemini call sites (Help Chat, OCR, CSV categorize) fire the `ai_call_metrics` Firebase Analytics event with feature label + prompt/cached/output token counts + pre-computed cache_hit_pct. Query in Firebase Analytics or via the existing BigQuery export. See the Monitoring section of [`feedback_gemini_prompt_caching.md`](feedback_gemini_prompt_caching.md) for cadence + thresholds + a starter BigQuery query.

### Sentiment scoring + Play Store review prompt (2026-05-20)

The Help Chat now does double duty: every Gemini reply also includes a 1-10 sentiment score for the user's latest message, computed by the model (1 = clearly negative — angry, confused, frustrated; 5 = neutral / pure factual question; 9-10 = unmistakable enthusiasm). The score is **internal** — never shown to the user, never mentioned by the bot in its visible reply. See `HelpChatPromptBuilder` rule 9 for the scale + clamping behavior.

How the score flows:
- `HelpChatGeminiService.reply(...)` now returns `Result<HelpChatReply>` with `text` + `sentiment` (clamped 1-10, defaults to 5 on missing/malformed). The response schema gained the `sentiment` INTEGER field.
- `HelpChatMessage` gained two nullable/default fields: `sentiment: Int?` and `isReviewPrompt: Boolean`. Bot messages from successful Gemini calls carry the sentiment that was attached to the user message that prompted them. The JSON persistence uses short field names `s` (sentiment) and `r` (isReviewPrompt) to keep the file compact.
- `HelpChatUploader` prefixes the bot's `x` field with `[N] ` in the Firestore log when sentiment is set AND stores the score as a separate queryable `s` field. The `[N]` prefix is for at-a-glance Firestore-console review; the `s` field is for BigQuery slicing ("show me all 9-10 turns this week" → audit sentiment accuracy).
- The in-app dialog DOES NOT display the score. Local `text` stays clean.

Play Store review prompt (gated on `SENTIMENT_REVIEW_THRESHOLD = 9` and a 2-day debounce):
- After a successful reply with sentiment ≥ 9, the dialog appends a SECOND bot message containing the localized `reviewPromptText`. The message is flagged `isReviewPrompt = true` and renders with an accent-tinted bubble; the entire bubble is `clickable` to open the Play Store listing (`market://details?id=…` with `https://play.google.com/...` web fallback for degoogled devices, debug suffix stripped so debug builds also link to the production listing).
- Debounce lives in **SharedPreferences** (`app_prefs.helpChatReviewPromptAt`, epoch ms), NOT the chat JSON, so it survives Clear and the 48 h message prune — the user can't bypass it by resetting the chat. `HelpChatStore.shouldShowReviewPrompt(ctx, sentiment)` and `markReviewPromptShown(ctx)` are the helpers.
- The review-prompt message is NOT a Gemini call — it carries no sentiment, doesn't burn daily quota, and skips the `[N]` log prefix on upload (sentiment is null, isReviewPrompt is true).

Why this design:
- "I'll pass on your feedback" wording (rule 8) replaced the older "the development team reviews these conversations" — sounds less like surveillance and is more honest about the workflow (we periodically have Claude summarize logs rather than read every word).
- Sentiment + the `[N]` prefix gives us a low-effort audit channel for "how well is Gemini judging tone?" without us building a custom rating UI.
- The 2-day debounce protects against the worst-case "user is being polite in three consecutive turns, gets three review nags in two minutes" failure mode.
- Threshold of 9 (not 8) keeps the prompt rare — only unmistakable enthusiasm triggers it. Casual "thanks" should score 7-8 and not trip it.
- Structure: big picture → glossary (Available Cash, set-aside, remembered amount, delete-vs-unlink, tiers) → screen tour of all 12 functional screens → money-math callouts (income modes, period rollover, set-aside math, savings floor, amortization, Supercharge, linking, auto-categorize, duplicate detection) → 12 step-by-step task recipes → error/warning explanations → tier reference → scope boundary.
- Synthesized from 5 parallel Explore-agent cluster sweeps (Dashboard/Sim/Calendar, Transactions/Amortization/CSV, RE/IS/SG, Settings/BudgetConfig/Backup, SYNC/Receipts/Widget/Themes/Billing). Each agent read code + help screen + matching memory specs + SSD/LLD sections, returned a UI inventory, money-math callouts, and a drift list.
- Drift fixes (BI_WEEKLY misrepresentation + SECURESYNC_CSV display-name clarification) landed in separate commits per source file (`7493a96` memory, `d5d3a09` LLD, `9418a41` SSD).
- KB is English-only — the system prompt's language-matching rule handles Spanish queries via in-context translation.

### Strings
- Added `thinkingLabel`, `errorReply`, `dailyLimitHint`, `emailSubject`, `emailBodyIntro` (5-string addition across the 4-file ritual). `emptyBody` reworded to drop "trained on the help pages" claim until the real KB lands.

---

## 🚧 TODO before merge to dev

### Cost ceiling Cloud Function
- Checks total daily Gemini calls against a project-level quota; serves a static "service temporarily unavailable, please email" past the threshold. Daily caps in the app are the first defense; this is the second.
- Particularly relevant now that the comprehensive KB makes each turn ~5–8 K input tokens (~$0.0006–$0.0008/turn at Standard pricing). A runaway loop or prompt-injection that echoes the KB back could each cost ~$0.10; the server guard catches what the daily caps miss.

### Manual QA on-device
- Solo user flow: consent → chat → upload → 7-day TTL sweep.
- SYNC user flow: consent → existing auth → upload.
- Daily-cap rollover: hit Free cap (10), verify Send disabled + placeholder swap; advance device clock by 1 day; verify cap resets.
- Email escape: confirm `mailto:support@techadvantageapps.com` opens with transcript + chat-id footer prefilled.
- Off-topic refusal: ask non-BudgeTrak question; verify polite refusal + email pointer.
- Comprehensive-KB validation: ask questions across income modes, delete-vs-unlink, set-aside math, SYNC member limit, etc.; verify answers are grounded in the KB.

### Tests + manual QA
- Solo user flow: consent → anonymous sign-in → upload → 7-day TTL sweep.
- SYNC user flow: consent → existing auth → upload.
- Consent revocation: uncheck in Settings → next chat open re-shows consent dialog.
- Clear button: uploads then wipes (offline path: wipes anyway, no upload).
- 48-hour local prune on load.

### Merge to dev
After backend wiring + KB + daily limits land and manual QA passes, merge `feature/help-chat` → `dev` and ship through the alpha track.

---

## Design choices locked in

- **Per-device consent in Settings**, not per-chat — friction tradeoff. The consent dialog appears once (or after revocation), persists via the Settings checkbox. The privacy policy + GDPR-style explicit-Accept satisfies the legal bar.
- **Anonymous transcript storage** — no Firebase UID, device ID, IP, name, or email stored alongside transcripts. Random 128-bit chatId is the only identifier. Privacy policy emphasizes this.
- **7-day server TTL via `expireAt` field**, recomputed on every upload — active chats keep refreshing the window; idle chats auto-expire.
- **48-hour local prune** — even without Clear, messages older than 48 h drop from the local buffer on next load.
- **Solo users included** — anonymous Firebase auth bootstraps automatically when needed. No SYNC requirement.

## Open questions / deferred
- Gate behind Paid from day one? (lower abuse risk, lower user value — currently planned as all-tiers with daily-limit differentiation).
- Voice input — out of scope for v1.
- Crashlytics linkage — link a chat session to a recent crash if any? — deferred.
- Vertex AI Abuse Monitoring opt-out (enterprise-only currently) — would let us flip Messages declaration to "Ephemeral: Yes". Soft trigger noted in `project_play_data_safety.md`.

## Cost ceiling (Flash-Lite pricing as of 2026-04)
- ~5K KB tokens + ~500 tokens per turn = ~$0.0004/turn input
- Output: ~300 tokens × $0.30/M = ~$0.0001/turn output
- Per conversation (5 turns): ~$0.002
- At 100K users firing 5 turns/day each: ~$200/day. Daily-limit caps make this manageable.
