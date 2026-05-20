# BudgeTrak Help Chat — Knowledge Base (v0 stub)

> This is a placeholder knowledge base used by the Help Chat assistant.
> Real content is generated from the in-app Help pages + a handwritten
> supplement in a later phase. Until then, the assistant should rely on
> the conversational hints below and defer to the Email button for
> anything it isn't sure about.

## About BudgeTrak

BudgeTrak is an Android personal-budgeting app by Tech Advantage LLC. It
tracks expenses, recurring bills, income sources, savings goals, and
amortized large expenses (e.g. car insurance paid every 6 months).
"Available Cash" on the Dashboard is the calculated amount safe to spend
without breaking any commitment for the rest of the current budget
period.

## Period

- The budget period defaults to monthly but is fully configurable in
  Settings → Period.
- The period rollover happens at the user's configured reset hour
  (default midnight) on the first day of the new period.
- At rollover, recurring expenses and income sources auto-roll forward;
  one-off transactions stay tied to the period they happened in.

## SYNC (multi-device)

- BudgeTrak SYNC lets up to 5 devices share the same budget data.
- Encryption is end-to-end with per-field ChaCha20-Poly1305. Server
  never sees plaintext financial data.
- To join an existing group, the inviter shares a join code from
  Settings → SYNC. Solo users do not need an account.

## Common questions

- **Why is my Available Cash negative?** — You have more committed than
  cash on hand. Either a recurring bill is due before more income lands,
  or a savings goal contribution is over-committed. The Simulation
  screen (Dashboard → Simulate) shows when the floor crosses zero.
- **Why does a transaction say "auto-categorized"?** — BudgeTrak's
  matcher assigned a category based on merchant name. Tap the
  transaction to confirm or change the category; once you do, it's
  remembered for that merchant.
- **How do I attach a receipt photo?** — Paid tier only. In the
  transaction dialog, tap the camera icon. Photos sync to other devices
  in the SYNC group.

## Tiers

- **Free** — full budgeting + sync + CSV import.
- **Paid** (one-time) — receipt photos, widget, custom themes, bigger
  CSV imports, AI auto-categorize during CSV import, Simulation screen.
- **Subscriber** (monthly/yearly) — everything in Paid, plus ad-free,
  receipt OCR (Gemini-powered), and unlimited SYNC.

## Errors users may mention

- "App Check failed" → usually transient, retry in a moment.
- "Sync paused" → device was offline; sync resumes automatically when
  online.
- "Backup file too large" → check Settings → Backups → photo inclusion.

## Off-topic handling

If the user asks about anything outside BudgeTrak (general finance
advice, tax law, other apps, jokes, world events, personal questions,
etc.), politely decline and remind them the Email button at the bottom
of the chat reaches a human at techadvantagesupport@gmail.com.
