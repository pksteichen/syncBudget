---
name: Help Chat assistant — feature in progress
description: AI-powered in-app help chat (Gemini), grounded on a curated knowledge base, with daily limit + off-topic refusal + email escape. UI shell + persistence + Firestore upload (anonymous, 7-day TTL) + Gemini wiring + daily caps + email escape shipped on feature/help-chat 2026-05-19; comprehensive KB + cost-ceiling Cloud Function still TODO before merge to dev.
type: project
originSessionId: ca028513-626b-45e8-ad1c-a1863966bd91
---
# Help Chat assistant — progress + TODO

Branch: `feature/help-chat` (off `dev`). Tip: `2f44e40` — Gemini Send + daily caps + email escape (2026-05-19 evening). Earlier checkpoints: `38b73cd` merge of v2.10.30 from dev, `473d746` UI shell, `208669a` + `1a617c8` memory updates. Keep the branch periodically rebased on or merged from dev so subsequent releases don't accumulate drift.

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

### Stub knowledge base
- `app/src/main/assets/help_chat_kb.md` — placeholder content covering Available Cash, period reset, SYNC basics, tiers, common errors, and the off-topic-refusal rule. Real KB still TODO (see below).

### Strings
- Added `thinkingLabel`, `errorReply`, `dailyLimitHint`, `emailSubject`, `emailBodyIntro` (5-string addition across the 4-file ritual). `emptyBody` reworded to drop "trained on the help pages" claim until the real KB lands.

---

## 🚧 TODO before merge to dev

### Comprehensive knowledge base
- Replace stub at `app/src/main/assets/help_chat_kb.md`. Build from `EnglishStrings` help blocks + a handwritten supplement covering paid-vs-subscriber tiers, common error remedies, SYNC setup.
- Bilingual question? — leaning toward English-only KB with prompt-driven translation for Spanish queries. The current prompt already includes a language-matching rule, so an English-only KB should work.

### Cost ceiling Cloud Function
- Checks total daily Gemini calls against a project-level quota; serves a static "service temporarily unavailable, please email" past the threshold. Daily caps in the app are the first defense; this is the second.

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
