---
name: Help Chat assistant — feature in progress
description: AI-powered in-app help chat (Gemini), grounded on a curated knowledge base, with daily limit + off-topic refusal + email escape. UI shell + persistence + Firestore upload (anonymous, 7-day TTL) shipped on feature/help-chat 2026-05-19; Gemini wiring + KB + daily-limit logic still TODO.
type: project
---

# Help Chat assistant — progress + TODO

Branch: `feature/help-chat` (off `dev`). Tip: `38b73cd` — a merge of dev's v2.10.30 release (Android 15 edge-to-edge cleanup) into the branch on 2026-05-19; the Help Chat code shipped in `473d746` + memory updates in `208669a` are preserved through the merge. Keep the branch periodically rebased on or merged from dev so subsequent releases don't accumulate drift.

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

## 🚧 TODO before merge to dev

### Gemini integration
- Wire `HelpChatGeminiService` (raw HTTP, mirroring the existing OCR/CSV pattern in `data/ai/`). Reuse the Android-cert-restricted API key (see `reference_gemini_api_key.md`).
- System prompt: strict scoping, KB-grounded, off-topic refusal with email escape pointer. Temperature 0.1–0.2. Model: Gemini 2.5 Flash-Lite.
- On Send: append user message → call Gemini with `system + KB + chatHistory + lastUserMessage` → append assistant reply → persist + trigger uploadIfStale.
- Error handling: network failure → show inline retry; API quota exceeded → soft block + email escape.

### Knowledge base asset
- Single markdown file in `app/src/main/assets/help_chat_kb.md`. Build step (or manual checkpoint) regenerates from `EnglishStrings` help blocks + a handwritten supplement covering paid-vs-subscriber tiers, common error remedies, SYNC setup.
- Bilingual question? — leaning toward English-only KB with translation prompt for Spanish queries. Decide before launch.

### Daily-limit logic
- `helpChatCount` + `helpChatResetDay` prefs. Reset at local midnight (reuse period-refresh logic).
- Caps: Free 10/day, Paid 25/day, Subscriber 50/day (tunable; can lower if cost grows).
- Cap exceeded → soft block in the input row with an "Email support" CTA pointing at the existing Email button.

### Email escape hatch (currently toast stub)
- Wire the Email button to `ACTION_SENDTO` mailto:techadvantagesupport@gmail.com with:
  - Subject prefilled with chat session topic (last user message excerpt or chatId).
  - Body prefilled with the chat transcript so context isn't lost.

### Cost ceiling guard
- Cloud Function checking total daily Gemini calls against a project-level quota; cuts off + serves a static "service temporarily unavailable, please email" message past the threshold.

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
