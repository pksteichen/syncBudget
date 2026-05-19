---
name: Help Chat assistant — planned future feature
description: AI-powered in-app help chat (Gemini), grounded on a curated knowledge base, with daily limit + off-topic refusal + email escape; logs land in Firestore with 7-day TTL for periodic accuracy/abuse review.
type: project
---

# Help Chat assistant — future TODO

## Goal
An in-app help chat that answers configuration / budgeting / how-to / error-message questions for users, grounded on a hand-curated knowledge base. Reduces support email volume; provides 24/7 instant guidance.

**Why:** Users hit common questions (period configuration, sync setup, "why is my budget negative", AI quirks). Email round-trips are slow and don't scale. A scoped LLM with a good knowledge base can deflect most.

**How to apply:** Build after the post-merge stabilization of the custom-themes feature. Not a near-term blocker. Reuse the existing Gemini raw-HTTP plumbing (already in use for OCR + CSV categorization).

## Architecture sketch

**Model**: Gemini 2.5 Flash-Lite. ~1M context window, ~$0.075 per million input tokens. Fast enough for a chat-style UX. Already integrated via the app's Android-cert-restricted API key (see `reference_gemini_api_key.md`).

**Knowledge base**: single markdown asset in `res/raw/` (or assets/) covering:
- Configuration (budget periods, reset day/hour, currency, income mode)
- Budgeting concepts (safe budget, savings goals, amortization, accelerated)
- How-to recipes for common tasks (set up SYNC, import bank CSV, restore backup)
- Error messages and remedies (permission denied, conflict warnings, sync repair)
- Limits / paid-vs-subscriber distinctions

Build step regenerates the KB from existing help-page strings + a handwritten supplement file. Keeps drift low.

**System prompt**: strict scoping. Refuses off-topic with a canned response. Tells the model to answer only from the provided KB; "I don't know — try the email button below" otherwise. Temperature 0.1–0.2.

**Multi-turn context**: each call sends KB + full chat history. At ~5K KB + ~200 tokens/turn, a 20-turn conversation is still well under context limits.

**Daily limit**: store `helpChatCount` + `helpChatResetDay` in `app_prefs`. Reset at local midnight (matching the period-refresh logic). Suggested caps:
- Free: 10 turns/day
- Paid: 25/day
- Subscriber: 50/day

Hard cap protects against billing surprises. Cap exceeded → soft block with link to the email escape.

**Email escape hatch**: `Intent.ACTION_SENDTO` to `techadvantagesupport@gmail.com` with:
- Subject prefilled with chat session topic (last user message excerpt).
- Body prefilled with the chat transcript so context isn't lost.

**UI**: separate ChatScreen reached from Settings → Help section, plus a button at the bottom of each Help page ("Ask a question"). Standard SyncBudgetTheme styling. Daily count visible in the header. Email button anchored at the bottom of the chat.

## Privacy

- Per-chat consent dialog (NOT just first-time). User taps "Start chat" → modal explains queries leave the device, are sent to Gemini, may be logged for review (see below), and the limit. User must accept each session.
- Never auto-include PII (transactions, amounts, merchant names). Only what the user types is sent.
- The app's privacy policy gets a new section covering help-chat data flow before this ships.
- Spanish disclosure mirrors English; both go through the four-file ritual.

## Chat log persistence (review-driven KB improvement)

**Storage**: Firestore collection `helpChatLogs/{anonChatId}` where `anonChatId` is a fresh UUID per chat session (not tied to deviceId for review log access). Each doc holds:
- `startedAt`: server timestamp
- `language`: "en" or "es"
- `tier`: "free" / "paid" / "subscriber"
- `turns`: array of `{role: "user"|"assistant", content: String, at: timestamp}`

**Retention**: 7-day TTL via Firestore TTL policy on a `expiresAt` field set at insert time (now + 7 days). Logs auto-purge.

**Security rules**: `helpChatLogs` is write-only for clients; only the dev account (or a Cloud Function) can read. Don't add admin-read for sync group admins.

**Periodic review loop**: Claude reads recent logs on request (`gh` or BigQuery export, similar to the Crashlytics tool pattern). Looks for:
1. Wrong / incomplete answers → update KB or system prompt.
2. Off-topic chatter that slipped through the scope filter → tighten the system prompt or add canned refusals for the new pattern.
3. Repeated questions on topics the KB doesn't cover → expand KB.
4. Abuse patterns (single user firing many off-topic queries) → adjust daily limit or add per-deviceId hashing for stricter throttling.

User explicitly OKs writing logs as part of the per-chat consent. The disclosure must call this out plainly.

## Cost ceiling

At Flash-Lite pricing:
- ~5K KB tokens + ~500 tokens per turn = ~$0.0004/turn input
- Output: ~300 tokens × $0.30/M = ~$0.0001/turn output
- Per conversation (5 turns): ~$0.002

At 100K users firing 5 turns/day each: ~$200/day. The daily limit + per-tier caps make this manageable; if growth makes it uncomfortable, gate the chat behind Paid.

Hard billing safety: a Cloud Function checks total daily Gemini calls against a project-level quota; cuts off at the threshold and serves a static "service temporarily unavailable, please email" message.

## Open questions / deferred
- Whether to gate behind Paid from day one (lower abuse risk, lower user value).
- Whether to integrate with Crashlytics (link a chat session to a recent crash if any).
- Whether to support voice input (likely no — out of scope for v1).
- Spanish quality: KB is bilingual or English-only with a translation step at inference?

## Prerequisites before starting
1. The custom-themes feature is stable on dev (✅ as of 2026-05-19).
2. Help-page content audit / cleanup so the KB has accurate source material (mostly done).
3. Privacy policy draft update reviewed.
4. Firestore TTL policy + security rules for `helpChatLogs` written.
5. Cloud Function billing-quota guard prototyped.

## Why not derivable from code
This file documents intent and design constraints for work that hasn't started. None of it lives in the codebase yet.
