---
name: OCR Spanish / Country setting (future work)
description: When adding Spanish translations or launching in LatAm/Spain, add a Country setting that drives language/currency/date-format/tax-vocab hints into the OCR prompt. Not urgent until non-English launch.
type: project
originSessionId: 1ebb1f67-d429-406a-b518-08881dc22bb6
---
When BudgeTrak adds Spanish translations or launches in a non-US market, add a user-facing **Country** setting (defaulted from Android locale) and feed concise locale hints into the OCR prompt.

**Why:** Temperature-0 Flash-Lite is inconsistent about inferring locale from receipt features (HK$ currency, US ZIP codes, etc.) — see the 2026-04-20 new-bank regression where `wm_mcdonalds_hk` kept reading DD/MM dates as MM/DD despite HK$ being plainly visible. Passing locale explicitly eliminates the guessing game and costs ~20 prompt tokens.

**How to apply:** One user-visible dropdown, derive everything else internally.

```
Country (dropdown, defaults from Android locale):
  US, MX, ES, AR, CL, CO, PE, UY, VE, …

Derives:
  language:      en | es | ca | eu …
  currency:      USD | EUR | MXN | CLP | COP | PYG | …
  dateFormat:    MM/DD/YYYY | DD/MM/YYYY
  decimalStyle:  period-decimal | comma-decimal
  hasCents:      true | false
  taxTerms:      ["tax"] | ["iva", "ieps"] | ["iva", "igv"] | …
```

Inject into the OCR prompt as one preamble line, e.g.:
```
User locale: MX (Spanish, MXN with centavos, DD/MM/YYYY, tax labeled "IVA" or "IEPS").
```

## Spanish-market gotchas to encode

1. **Date**: nearly universal DD/MM/YYYY across ES + LatAm. Mexico is the only country with mixed US-style receipts from NAFTA-era POS systems.

2. **Number format** — two flavors:
   - European / most LatAm (ES, AR, CL, CO, PE, UY): `1.234,56` (period = thousand, comma = decimal)
   - Mexico: typically US-style `1,234.56 MXN`

3. **Integer-only currencies** (the VND problem recurs):
   - **CLP** (Chilean peso) — no cents, dots are thousands
   - **COP** (Colombian peso) — centavos exist on paper only
   - **PYG** (Paraguayan guaraní) — no cents
   - `hasCents: false` flag must carry into the C1.5 reconciliation prompt, otherwise the model divides 100× "to normalize to cents" — same bug observed on VND mcocr_* receipts.

4. **Tax vocabulary** — "Tax" never appears:
   - IVA (most countries)
   - IEPS (Mexico, excise)
   - IGV (Peru)
   - ISC (some countries)
   - `grader.js` / `validate-*.js` `isTax()` regex currently matches only `/\btax\b/i` — needs `/\b(tax|iva|ieps|igv|isc)\b/i` when expanding to Spanish markets.

5. **Total labels**: `Total`, `Importe`, `Total a pagar`, `Total neto`, `Importe total` (pre-tax vs post-tax distinction matters — `TOTAL neto` is the pre-tax net, not what the shopper paid).

6. **Legal-vs-consumer merchant**: Mexican and Spanish receipts almost always print a prominent `Razón Social` / `NIF` legal name. The existing "consumer brand over legal operator" rule already covers this case correctly.

## Scope guidance

- Not urgent: app is English-only, US-only as of 2026-04-20.
- Best time to add: simultaneous with first Spanish translation milestone.
- LatAm launch (especially Chile/Colombia/Paraguay) cannot ship without the integer-currency flag — amount accuracy will be 0% otherwise, as demonstrated by VN receipts in the 2026-04-20 regression.
