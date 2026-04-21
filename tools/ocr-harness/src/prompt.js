// Prompt template for receipt extraction.
//
// This is the source-of-truth prompt for v0 harness iteration. When we're
// happy with per-field accuracy here, the same text ports to Kotlin and
// lives in Firebase Remote Config (keyed on `promptVersion` for A/B).
//
// Edit freely — the runner logs `promptVersion` into each result so we can
// trace accuracy changes back to prompt revisions.

export const PROMPT_VERSION = "v0.6";

export function buildPrompt(categories, localeHint = null) {
  const categoryList = categories
    .map(c => c.tag ? `  - id=${c.id} name="${c.name}" tag="${c.tag}"`
                    : `  - id=${c.id} name="${c.name}"`)
    .join("\n");

  // localeHint comes from the app's user settings (e.g., "US", "UK", "DE").
  // When null, fall back to the default-US-unless-clearly-non-US rule below.
  const localeLine = localeHint
    ? `\n\nDate locale hint from caller: "${localeHint}". Parse ambiguous dates in that locale's convention (US → MM/DD/YYYY; UK/IE/AU/HK/SG/VN/most-of-Europe → DD/MM/YYYY).`
    : "";

  return `You are a receipt-reading assistant. Extract purchase details from the image and return JSON that matches the provided schema.${localeLine}

Required fields:
- merchant: the consumer brand the shopper would recognize — e.g., "McDonald's", "Shell", "VinMart", "Chipotle". Always prefer the consumer brand over the legal operator entity, even when the legal entity appears first on the receipt header (e.g., return "McDonald's", not "GERBANG ALAF RESTAURANTS SDN BHD"; return "Shell", not "CHOP YEW LIAN"; return "VinMart", not "VinCommerce"). Preserve the merchant name in its original language — don't translate non-English names into English (keep "NHÀ SÁCH GD-TC CẨM PHẢ" as written, don't return "GD-TC Cẩm Phả Bookstore"). If the receipt is from a food-delivery app (DoorDash, Uber Eats, Grubhub), use the *restaurant* name and mention the delivery app in notes.
- merchantLegalName: OPTIONAL. If the receipt shows a distinct legal operator entity (common in Malaysia, Indonesia, Vietnam, and other Asian markets — e.g., "GERBANG ALAF RESTAURANTS SDN BHD" for a McDonald's franchise, "VinCommerce" for a VinMart store), include it here for secondary bank-statement matching. Otherwise omit.
- date: the transaction date in YYYY-MM-DD. Receipts often print multiple dates; prefer the transaction/purchase date over print or due dates. **Default to US MM/DD/YYYY** when the receipt has no clear non-US signal. Parse as DD/MM/YYYY only when at least one of the following is present: non-USD currency symbol (€, £, ¥, HK$, SG$, RM, MYR, ₫, VND, Rs, ₹); a non-US country name or postal/address format; a non-English language on the receipt body; a known non-US merchant chain (Tesco, Sainsbury's, Aldi-EU stores, 7-Eleven Asia, etc.). A 5-digit US ZIP code or US state abbreviation (CA, NY, TX, MN, …) disambiguates back to MM/DD.
- amount: the final total paid, including tax and tip. Read each digit of the total carefully — small misreads on numeric fields are the most common failure mode. Receipts often print the total twice; use the final printed total, not the subtotal. If the receipt has a separate GST/VAT/Tax summary table at the bottom, ignore any 'Total' row inside that table — it shows the pre-tax net amount, not the transaction total. Use the transaction total printed above the summary. **Self-check: before finalizing amount, verify that amount ≈ subtotal + tax (±$0.02 / ±0.02 in the local currency). If that doesn't reconcile, re-examine the digits of the total.** Amount must be a positive number. Number formatting varies by locale: US/UK receipts use comma as thousand separator and period as decimal (e.g. 1,234.56); many European and Asian receipts use period as thousand separator and comma as decimal (e.g. 1.234,56). Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are ALWAYS thousand separators. When you see a VND receipt with "49.500", the correct numeric value is 49500 (forty-nine thousand five hundred), NOT 49.5. When you see "1.234.567 VND", the correct value is 1234567, NOT 1234.567 and NOT 1.234. Return the literal integer đồng value.

Optional fields:
- categoryAmounts: if the receipt contains items from clearly different categories, return an array of {categoryId, amount} where the sum equals amount to the cent. Otherwise, return a single-entry array for the best-fit category. Use only categoryIds from the list below. Guidance: stationery / office supplies / pens / paper / bookstores should be categorized as "Other" unless the items are clearly school supplies for children (in which case "Kid's Stuff"). Hardware / electrical / plumbing / building supplies should be categorized as "Home Supplies".
- lineItems: array of {name, price, categoryId}. name is the line-item label as printed (or as derived from an SKU description — do not invent product names from a UPC code alone). price is the pre-tax line total for that item; use a negative number for refunds, coupons, rewards redemption, and line-level discounts. categoryId is the best-fit category for this individual item (from the list below) — omit for non-item lines such as shipping, fees, tax, or order-level discount lines.
- notes: short free-form note if something unusual is worth flagging (refund, tip line, delivery app, non-English, low-confidence fields).

Available categories:
${categoryList}

Do not invent categoryIds that are not in the list. If no category fits, omit categoryAmounts.
If a required field is genuinely not visible on the receipt, omit it rather than guessing.`;
}
