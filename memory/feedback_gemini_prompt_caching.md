---
name: Gemini prompt caching — preamble-first ordering + monitoring + scale plan
description: Always emit the stable preamble (system prompt + KB / instructions) FIRST in any Gemini call so Google's implicit prompt cache can fire. Per-call usage is logged via the ai_call_metrics Analytics event — check Firebase Analytics / BigQuery roughly monthly to see cache hit ratio per feature. At higher volumes, plan to migrate to explicit caching behind a server-side proxy. Don't interpolate per-device variables into the preamble — they fragment the cache.
type: feedback
originSessionId: ca028513-626b-45e8-ad1c-a1863966bd91
---
# Gemini prompt caching — current strategy + future plan

## Rule: always ship the stable preamble first

When constructing prompts for ANY Gemini call (Help Chat, receipt
OCR, CSV auto-categorize, anything new), put the stable / repeated
content FIRST and the variable per-request content LAST:

```
[stable preamble — system prompt + rules + KB/instructions + output-format reminder]
[chat history / context — semi-stable within a session]
[the latest user message / variable input — always changes]
```

Why this matters: Google's implicit prompt cache for Gemini 2.5
Flash-Lite (and other 2.5-family models) hashes shared prefixes and
caches any prefix ≥ ~1–2 K tokens that matches across requests. The
cache is keyed by **API key + prefix hash** — IP doesn't matter for
the lookup itself. Every byte after the first mismatch invalidates
the cache.

So:
- Stable bytes at the front → big shared cache hit → ~10× discount
  on the cached portion (Standard tier: input $0.10/M → cached input
  $0.01/M).
- A per-device variable in the front (locale tag, user id, timestamp,
  random salt) fragments the cache per-device and kills the benefit.
- A stable bit at the *end* (e.g., a trailing schema reminder) is
  wasted — anything past the variable suffix isn't reused.

**How to apply:** assemble Gemini prompts in two parts — a `preamble`
string built only from constants + the asset-file KB, and a `suffix`
built from per-turn variables — and concatenate `preamble + suffix`.
Resist the urge to interpolate "helpful" device hints (locale, region,
timezone, device model, anonymous id) into the preamble. If a hint is
needed, put it in the suffix or rely on the model inferring from the
user's actual message.

## Concrete fix (Help Chat, 2026-05-19, commit `5136a6d`)

`HelpChatPromptBuilder.kt` v1 interpolated `Locale.getDefault()
.toLanguageTag()` into rule #5 of the system prompt. That made the
preamble byte-different per device locale, so en-US and es-419 users
couldn't share an implicit cache. v2 dropped the locale interpolation
and relies on rule #5's "match the language of the user's latest
message" instruction (which is the dominant signal anyway). The
preamble + KB block is now byte-identical across all devices and all
turns, and was restructured into an explicit `preamble + suffix`
assembly with a `---` separator so the boundary is obvious to future
maintainers. The trailing JSON-format reminder also moved into the
preamble (as rule #8) so it benefits from the cache. The system
prompt no longer names a support email — the chat dialog already has
a literal Email button.

## Implicit-cache benefit at current scale

Help Chat after the comprehensive KB landed (commit `885fffa`):
- Preamble (system prompt + KB) ≈ ~22 K input tokens, byte-identical.
- Variable suffix (history + latest user message) ≈ 0.5–2 K tokens.
- Cache hit on the preamble saves: 22 K × ($0.10/M − $0.01/M) ≈
  **$0.002 per cached turn**. Free at our current scale (Google
  handles it; no config required); the only requirement is the
  byte-identical preamble.
- Daily caps (Free 10 / Paid 25 / Subscriber 50) keep total volume
  manageable; per-turn input cost is the dominant variable in the
  cost model.

## Monitoring — `ai_call_metrics` event (shipped 2026-05-20)

`GeminiHttpClient.generate` accepts a `usageCallback: (UsageMetadata) -> Unit` parameter that fires after every successful response. All three Gemini call sites (Help Chat, OCR, CSV auto-categorize) wire the callback to `AnalyticsEvents.logAiCallMetrics`, which emits a Firebase Analytics event named **`ai_call_metrics`** with these params:

| Param | Type | Meaning |
|---|---|---|
| `feature` | string | `"help_chat"` / `"ocr"` / `"csv_categorize"` |
| `model` | string | e.g. `"gemini-2.5-flash-lite"` |
| `prompt_tokens` | long | total input tokens billed |
| `cached_tokens` | long | subset that hit the implicit cache (priced at $0.01/M vs $0.10/M) |
| `output_tokens` | long | completion tokens billed at $0.40/M |
| `cache_hit_pct` | long | `cached_tokens × 100 / prompt_tokens`, 0–100 (pre-computed so dashboards can bucket easily) |

Gated by the existing `crashlyticsEnabled` opt-out (same toggle as Crashlytics + Analytics).

### Checking the data — recommended ritual

**When to check:** roughly **once per month**, plus any time you (a) ship a Help-Chat prompt change, (b) ship a KB change of any size, (c) deploy to a new region / track / userbase. Also on demand if billing looks higher than expected.

**Where to look:**

1. **Firebase Console → Analytics → Events → `ai_call_metrics`** — gives you a 30-day count of events, plus per-param distributions. Easy first-look — eyeball `cache_hit_pct` distribution sliced by `feature`. Help Chat should be the dominant feature with a hit ratio that grows toward 90–100% as DAU grows; OCR will sit near 0% (expected — each receipt is unique, nothing to cache); CSV categorize will be intermittent.

2. **BigQuery export** (`analytics_<projectId>.events_*`) for richer slicing — per-region cache hit ratio, week-over-week trends, per-feature cost projections. Example query stub (replace project + date range):
   ```sql
   SELECT
     event_date,
     (SELECT value.string_value FROM UNNEST(event_params) WHERE key='feature') AS feature,
     COUNTIF((SELECT value.int_value FROM UNNEST(event_params) WHERE key='cache_hit_pct') > 50) AS hits,
     COUNTIF((SELECT value.int_value FROM UNNEST(event_params) WHERE key='cache_hit_pct') = 0) AS misses,
     SUM((SELECT value.int_value FROM UNNEST(event_params) WHERE key='prompt_tokens')) AS total_input_tokens,
     SUM((SELECT value.int_value FROM UNNEST(event_params) WHERE key='cached_tokens')) AS total_cached_tokens
   FROM `<projectId>.analytics_<id>.events_*`
   WHERE event_name = 'ai_call_metrics'
     AND _TABLE_SUFFIX BETWEEN '20260520' AND FORMAT_DATE('%Y%m%d', CURRENT_DATE())
   GROUP BY event_date, feature
   ORDER BY event_date, feature;
   ```
   The BigQuery service-account credentials are in `reference_bigquery_service_account.md`.

3. **Google Cloud Console → Billing → Reports** — cross-check by SKU. The `Gemini 2.5 Flash-Lite Cached Input Tokens` line item is what should grow as caching works.

### What the data tells us — actionable thresholds

- **Help Chat `cache_hit_pct` consistently > 80%**: implicit caching is doing its job; no action needed.
- **Help Chat `cache_hit_pct` 30–80%**: cache is partially warm; could be normal at low DAU but worth re-checking next cycle. If it stays in this range while DAU grows, that's a signal of regional cache fragmentation.
- **Help Chat `cache_hit_pct` stuck < 30% with growing DAU**: something is breaking the preamble byte-identity (a recent code change accidentally interpolated a per-device variable?), OR regional fragmentation is severe enough to warrant the explicit-cache + server-proxy migration described below.
- **Any feature's `prompt_tokens` median jumps unexpectedly**: KB or system prompt grew — verify intentional.
- **Sudden drop in event volume**: callers may have lost the callback wiring after a refactor — re-verify all three Gemini call sites still pass `usageCallback`.

### Reminder for Claude (future sessions)

When the user opens a help-chat-related task, mentions "cost", "billing", "Gemini", "cache", or any recent Help-Chat / OCR / CSV change, **proactively suggest they check the latest `ai_call_metrics` data** before making cost-related decisions (especially anything that touches the prompt builder, KB, or the in-app daily caps). The data lives in Firebase Analytics under that exact event name.

## Regional caveat (matters at high volume)

Implicit caches are per-region. Google routes API calls to the
nearest data center, so a Tokyo user can't read a cache warmed in
Texas seconds earlier. At our scale this doesn't matter; at high
volume (10 K+ DAU) it could mean each region needs enough traffic
to keep the cache warm or hit rate degrades.

## When to migrate to explicit caching + server proxy

If usage ever reaches ~10 K+ DAU or regional warmth becomes a visible
cost item, plan to:

1. Stand up a server-side proxy (Cloud Function in a fixed region).
2. Devices send only their dynamic suffix to the proxy via our own
   lightweight auth.
3. Proxy holds the Gemini API key (no longer embedded in the APK),
   prepends the preamble, and uses **explicit caching** (refreshed
   once an hour by the proxy) so every device request guarantees a
   ~100 % cache hit.
4. Explicit caching has storage cost (~$1 / M tokens / hour for the
   cached prefix). At our 22 K-token preamble that's ~$0.02/hour =
   ~$15/month per region — pays back at high volume, not at ours.

Until then, the Android-cert-restricted API key (see
`feedback_android_cert_headers_for_google_apis.md`) covers the
"embedded API key" security concern: an extracted key won't validate
against Google's gateway unless paired with the registered
`com.techadvantage.budgetrak` package and the production SHA-1.

## Cross-reference

- Help Chat daily-cap design + per-turn cost math: `project_help_chat_assistant.md`.
- Cert-restricted-key mitigation: `feedback_android_cert_headers_for_google_apis.md`.
- The cost-ceiling Cloud Function (still TODO) is a circuit breaker
  for runaway-cost scenarios regardless of caching — it's separate
  from the caching architecture, not a replacement for it.
