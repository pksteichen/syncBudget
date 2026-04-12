---
name: Subscriber feature ideas
description: Brainstormed list of features that could push free/paid users to the monthly subscriber tier. Each candidate is rated on engineering effort, ongoing cost (which justifies monthly billing), and whether it's regional or universal.
type: project
originSessionId: e62277a3-386c-4af8-8747-78a2f79a4bee
---
Brainstormed during 2026-04-11 conversation while thinking about what would drive subscription conversions. Bank-feed aggregators (Plaid/Teller/etc.) were ruled out as the primary subscriber draw because they're regional, expensive at small scale ($500–1500/month minimums for Plaid), and require per-region integration work.

The bar for any candidate: it should either have a real ongoing cost on our side (so monthly billing isn't arbitrary), save the user time repeatedly enough they actually feel the value, or unlock something they couldn't reasonably do themselves with the free tier.

## Top contenders (build these first)

**Receipt + Screenshot OCR via multimodal LLM.** Snap a receipt photo OR share a screenshot from any app (DoorDash, Amazon, Uber Eats, bank notification, etc.) and BudgeTrak extracts merchant, amount, date, and a category hint, then saves a transaction with the image attached. Uses Claude Haiku 4.5 vision directly on the image (no OCR-then-parse), ~$0.002–0.005 per call. Android share-intent integration so it works from any app's share sheet. Strongest single feature on this list — see [project_ocr_receipt_capture.md](project_ocr_receipt_capture.md) for the dedicated plan.

**Smart Alerts / Budget Coach.** Always-on local intelligence using data already in BudgetCalculator. Examples: "You're 70% through the week but you've spent 95% of your weekly budget — slow down." "Based on your last 90 days, you're projected to overspend rent month next month, here's why." "Three subscriptions are about to renew this week totaling $42." Mostly local computation (no per-user cost) but high perceived value. Push-notification delivery brings lapsed users back. Build effort: medium (a week or two) — most of the math is already in BudgetCalculator, the new work is the alert engine, scheduling, notification UX, and a settings panel for which alerts the user wants.

**AI Monthly Insights.** Once a month, send subscribers a summary: "You spent 23% more on dining than last month. Your three biggest unexpected expenses were X, Y, Z. At your current pace, you'll end the month $80 under budget." Two implementation paths: simple stats over the existing transaction store (zero ongoing cost), or pipe a summarized JSON of the month into a small Claude Haiku call (couple of cents per user per month) for more natural-language insight. Either way the perceived intelligence is high. "Your monthly insight is ready" is a great re-engagement push. Pairs naturally with Smart Alerts.

## Strong candidates (build after the top three prove out)

**Email-forwarded transaction capture.** Each subscriber gets a unique forwarding address (`u-abc123@inbox.budgetrak.app`); they set up a filter in their email to forward bank notification emails there, and the server parses each email into a pending transaction the user reviews. Works in every country with no aggregator deal — just bank notification emails, which most banks worldwide send. Ongoing cost is a cheap email service (Postmark / AWS SES, ~$10/month flat at low volume). Engineering is real: server-side inbox handler, parser per bank format, push delivery into the app. Bigger project than OCR, more novel as a differentiator.

**Cloud backup history with restore points.** Extends the existing auto-backup. Subscribers get the last 30 daily snapshots stored in Cloud Storage with one-tap restore to any of them. The "I accidentally deleted six months of transactions" story sells itself. Real ongoing storage + bandwidth cost but tiny per user. Engineering is small because it builds on the existing backup pipeline.

**Multi-currency with auto-conversion.** Travel users and remote workers struggle with this. Subscribers enter transactions in any currency and have them converted to their home currency using daily exchange rates (free or cheap APIs like exchangerate.host or Open Exchange Rates). Niche but vocal audience, geographically broadens BudgeTrak's appeal. Engineering is medium — touches the data model, display layer, and BudgetCalculator.

**Splitwise-style shared tabs.** SYNC handles couples and households well, but lots of people want to track "what does my roommate owe me" without giving them access to the whole budget. Lightweight expense splits with anyone, settlement tracking, no group needed. Addresses a use case SYNC doesn't, the audience is bigger than you'd guess. Engineering is real but contained.

## Skeptical / probably skip

**Premium themes and cosmetic customization.** People who care about themes are not the people who pay monthly for finance apps. Real data on this from other budgeting apps.

**Web/desktop companion.** Money pit unless you actively want the engineering challenge. Solo-dev maintenance cost is high.

**Voice entry via Siri / Google Assistant.** Platform-locked, rarely used in practice for transaction entry.

**Tax export / categorization.** Sounds great in theory; in practice users do it once a year and resent paying twelve months for a one-month feature.

## Recommended sequencing

Pick one workflow win (Receipt + Screenshot OCR) and one always-on intelligence feature (Smart Alerts or Monthly Insights). Ship both as the v2.6 subscriber expansion. See whether the conversion needle moves before committing to anything bigger like Email-forwarded capture or Shared Tabs. Two well-built features with clear daily value beat seven half-built ones every time.
