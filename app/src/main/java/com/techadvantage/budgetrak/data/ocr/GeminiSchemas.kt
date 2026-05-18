package com.techadvantage.budgetrak.data.ocr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Static JSON schemas for the OCR pipeline calls. These replace the
 * SDK's `Schema.obj(...)` / `Schema.str(...)` builders with direct
 * JSON construction matching the Gemini OpenAPI subset:
 *
 *   { "type": "OBJECT|STRING|INTEGER|BOOLEAN|ARRAY",
 *     "description": "...",
 *     "properties": { ... },
 *     "items": { ... }  (for ARRAY) }
 *
 * All values that the SDK marked required are listed in `required`
 * arrays per object level.
 */
internal object GeminiSchemas {

    // ── Primitive builders ─────────────────────────────────────────

    private fun str(description: String) = JSONObject()
        .put("type", "STRING")
        .put("description", description)

    private fun int(description: String) = JSONObject()
        .put("type", "INTEGER")
        .put("description", description)

    private fun bool(description: String) = JSONObject()
        .put("type", "BOOLEAN")
        .put("description", description)

    private fun arr(description: String, items: JSONObject) = JSONObject()
        .put("type", "ARRAY")
        .put("description", description)
        .put("items", items)

    /** Build an OBJECT schema with named properties; `required` defaults to all keys. */
    private fun obj(
        description: String,
        properties: Map<String, JSONObject>,
        required: List<String> = properties.keys.toList()
    ): JSONObject {
        val props = JSONObject()
        for ((k, v) in properties) props.put(k, v)
        return JSONObject()
            .put("type", "OBJECT")
            .put("description", description)
            .put("properties", props)
            .put("required", JSONArray(required))
    }

    // ── Schemas (match the originals in ReceiptOcrService.kt) ──────

    val call1: JSONObject = obj(
        description = "Receipt header + purchased item name list",
        properties = linkedMapOf(
            "merchant" to str("Consumer brand on the receipt header"),
            "merchantLegalName" to str("Optional legal operator entity"),
            "date" to str("Transaction date in YYYY-MM-DD"),
            "amountCents" to int("Final total paid, integer cents"),
            "itemNames" to arr(
                "Every purchased line, as printed. Skip promos/coupons/discounts/tenders/subtotals. Include tax lines (Sales Tax, Estimated tax, etc.) as their own entry.",
                str("One receipt line, verbatim")
            ),
            "fullTranscript" to arr(
                "Focused transcript used by the Call 1.5 reconciliation step. Include ONLY lines with a calendar date, a monetary amount, Subtotal/Total/Tax labels, or merchant header. Skip barcodes, policy boilerplate, signatures. 10-30 lines is typical.",
                str("One receipt line, verbatim")
            ),
            "notes" to str("Optional free-form note")
        )
    )

    val call1r: JSONObject = obj(
        description = "Reconciled merchant + date + amount after cross-checking against the transcript",
        properties = linkedMapOf(
            "merchant" to str("Reconciled consumer-brand merchant name"),
            "date" to str("Reconciled transaction date in YYYY-MM-DD"),
            "amountCents" to int("Reconciled final total paid, integer cents"),
            "notes" to str("Optional one-sentence explanation when values changed")
        )
    )

    val call2: JSONObject = obj(
        description = "Per-item category scores + routing decision",
        properties = linkedMapOf(
            "items" to arr(
                "Per-item category scoring, SAME ORDER as the input name list",
                obj(
                    description = "A line item with up to 3 scored category candidates",
                    properties = linkedMapOf(
                        "description" to str("Item text as printed on the receipt"),
                        "scores" to arr(
                            "Up to 3 best-fit categories, descending by score",
                            obj(
                                description = "A category candidate with a 0-100 match score",
                                properties = linkedMapOf(
                                    "categoryId" to int("Category id from the provided list"),
                                    "score" to int("Match strength 0-100"),
                                    "reason" to str("Brief (≤15 words) justification")
                                )
                            )
                        )
                    )
                )
            ),
            "multiCategoryLikely" to bool("True when items' top-1 domains differ"),
            "topChoice" to int("Best-fit category id for the whole receipt when multiCategoryLikely is false")
        )
    )

    val call3: JSONObject = obj(
        description = "Prices per line item",
        properties = linkedMapOf(
            "prices" to arr(
                "Parallel array to the input item list; priceCents per item",
                obj(
                    description = "Price in integer cents",
                    properties = linkedMapOf(
                        "description" to str("Item text (mirrors input)"),
                        "priceCents" to int("Paid price in cents after line discounts")
                    )
                )
            )
        )
    )
}
