package com.techadvantage.budgetrak.data.ocr

import com.techadvantage.budgetrak.data.Category

// R7-T10 winner from the offline OCR harness (Round 7, variant T10).
// Configuration: category rules (C3+C4+C6+MP) prepended BEFORE the base extraction
// prompt. Benchmarked at 85%/99%/100%/65% (merchant/date/amount/cset) on 158
// app-quality receipts with Gemini 2.5 Flash at temperature 0.
//
// Source of truth: tools/ocr-harness/src/prompt.js + scripts/iterate-round7.js.
// When iterating further in the harness, keep this file in sync.

const val OCR_PROMPT_VERSION = "R7-T10"

fun buildOcrPrompt(categories: List<Category>): String {
    val filtered = categories.filter { it.tag != "supercharge" && it.tag != "recurring_income" && !it.deleted }
    val categoryList = filtered.joinToString("\n") { c ->
        if (c.tag.isNotEmpty()) "  - id=${c.id} name=\"${c.name}\" tag=\"${c.tag}\""
        else                    "  - id=${c.id} name=\"${c.name}\""
    }

    val c3 = """

CATEGORY RULE — itemize then consolidate:
Step 1: For each line item, assign {description, price, categoryId}. Step 2: Group by categoryId and sum. Step 3: Return one categoryAmounts entry per distinct categoryId."""

    val c4 = """

CATEGORY RULE — use tax markers:
Receipts often mark each item with tax codes (T, F, N, S, O). Taxed non-food (T) → Home Supplies (30186) or Other (30426); non-taxed (N, F) → Groceries (22695)."""

    val c6 = """

CATEGORY RULE — detailed category→item mapping:
Food/produce/meat/pantry → 22695 Groceries.
Beverages from a cafe → 21716; bottled drinks from a supermarket → 22695.
Cleaning, paper towels, ziploc/foil, pet items → 30186 Home Supplies.
Batteries, stationery, pens, office supplies → 30426 Other.
Kids' toys, school workbooks, kids clothes → 1276 Kid's Stuff.
Pharmacy/OTC medicine → 17351 Health/Pharmacy.
Fuel, parking, tolls → 48281 Transportation/Gas.
Work safety gear, uniforms → 47837 Employment Expenses.
Hardware, electrical, lighting, paint → 30186 Home Supplies."""

    val mp = """

PRIORITY REMINDER: merchant and amount are the most important fields. Do not compromise them while attending to category work. Merchant MUST be the consumer brand (not a cashier name, customer name, or translated English word)."""

    val flashBase = """You are a receipt-reading assistant. Extract purchase details from the image and return JSON that matches the provided schema.

Required fields:
- merchant: the consumer brand the shopper would recognize — e.g., "McDonald's", "Shell", "VinMart", "Chipotle". Always prefer the consumer brand over the legal operator entity, even when the legal entity appears first on the receipt header (e.g., return "McDonald's", not "GERBANG ALAF RESTAURANTS SDN BHD"; return "Shell", not "CHOP YEW LIAN"; return "VinMart", not "VinCommerce"). Preserve the merchant name in its original language — don't translate non-English names into English (keep "NHÀ SÁCH GD-TC CẨM PHẢ" as written, don't return "GD-TC Cẩm Phả Bookstore"). If the receipt is from a food-delivery app (DoorDash, Uber Eats, Grubhub), use the *restaurant* name and mention the delivery app in notes.
- merchantLegalName: OPTIONAL. If the receipt shows a distinct legal operator entity (common in Malaysia, Indonesia, Vietnam, and other Asian markets — e.g., "GERBANG ALAF RESTAURANTS SDN BHD" for a McDonald's franchise, "VinCommerce" for a VinMart store), include it here for secondary bank-statement matching. Otherwise omit.
- date: the transaction date in YYYY-MM-DD. Receipts often print multiple dates; prefer the transaction/purchase date over print or due dates. Pay attention to locale: Malaysian, Singaporean, Vietnamese, and most European receipts use DD/MM/YYYY (day first); US receipts use MM/DD/YYYY (month first). If the receipt language or merchant address indicates a DD/MM locale, parse dates accordingly.
- amount: the final total paid, including tax and tip. Read each digit of the total carefully before returning — small misreads on numeric fields are the most common failure mode. Receipts often print the total twice; use the final printed total, not the subtotal. If the receipt has a separate GST/VAT/Tax summary table at the bottom, ignore any 'Total' row inside that table — it shows the pre-tax net amount, not the transaction total. Use the transaction total printed above the summary. Amount must be a positive number. Number formatting varies by locale: US/UK receipts use comma as thousand separator and period as decimal (e.g. 1,234.56); many European and Asian receipts use period as thousand separator and comma as decimal (e.g. 1.234,56). Vietnamese đồng (VND) has no fractional unit — dots in VND amounts are ALWAYS thousand separators. When you see a VND receipt with "49.500", the correct numeric value is 49500 (forty-nine thousand five hundred), NOT 49.5. When you see "1.234.567 VND", the correct value is 1234567, NOT 1234.567 and NOT 1.234. Return the literal integer đồng value.

Optional fields:
- categoryAmounts: if the receipt contains items from clearly different categories, return an array of {categoryId, amount} where the sum equals amount to the cent. Otherwise, return a single-entry array for the best-fit category. Use only categoryIds from the list below. Guidance: stationery / office supplies / pens / paper / bookstores should be categorized as "Other" unless the items are clearly school supplies for children (in which case "Kid's Stuff"). Hardware / electrical / plumbing / building supplies should be categorized as "Home Supplies".
- lineItems: visible product names, SKU descriptions, or category codes as plain strings. Do not guess products from a UPC code alone.
- notes: short free-form note if something unusual is worth flagging (refund, tip line, delivery app, non-English, low-confidence fields).

Available categories:
$categoryList

Do not invent categoryIds that are not in the list. If no category fits, omit categoryAmounts.
If a required field is genuinely not visible on the receipt, omit it rather than guessing."""

    // R7-T10 cat-first ordering: category rules prepended BEFORE the base extraction prompt.
    return c3 + c4 + c6 + mp + "\n\n" + flashBase
}

/**
 * Simplified Lite prompt for single-category duty (variant B_bare from harness
 * A/B). Drops the multi-cat process rules (C3/C4/C6/MP) — harness showed
 * effectively identical single-cat accuracy with ~35% fewer input tokens and
 * a cleaner job description. Used when the user has pre-selected 0-1
 * categories (multi-cat pre-selections route to Pro).
 *
 * Schema note: Lite uses amountCents (integer) to sidestep float-parsing
 * issues on locale-specific number formats (Vietnamese VND thousand-separator
 * dots, etc.). The service converts to a Double before returning OcrResult.
 */
fun buildLiteOcrPrompt(categories: List<Category>): String {
    val filtered = categories.filter { it.tag != "supercharge" && it.tag != "recurring_income" && !it.deleted }
    val categoryList = filtered.joinToString("\n") { c ->
        if (c.tag.isNotEmpty()) "  - id=${c.id} name=\"${c.name}\" tag=\"${c.tag}\""
        else                    "  - id=${c.id} name=\"${c.name}\""
    }

    return """Extract receipt data as JSON: {transcription, merchant, merchantLegalName?, date, amountCents (integer), categoryAmounts?}.

Stage 1: transcription = every line of the receipt as plain text.
Stage 2: extract remaining fields.

Amount is INTEGER CENTS. Ignore GST-summary pre-tax rows.

- date MUST be YYYY-MM-DD ISO.
- merchant is the consumer brand. Preserve original language.
- merchantLegalName is optional.
- Do NOT return a cashier/customer personal name as merchant.
- Category ids must come from:
$categoryList"""
}
