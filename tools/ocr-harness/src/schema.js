// responseSchema for both Gemini (structured output) and our own post-hoc
// validation. Gemini accepts an OpenAPI-ish subset; Anthropic doesn't enforce
// a schema server-side so we validate manually after parsing.
//
// Design note: `merchant`, `date`, `amount` are the only truly required fields.
// Everything else is optional — the model omits rather than hallucinates when
// unsure, which is the behavior we want for the schema-gap re-ask path.

export const RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    merchant: { type: "string", description: "Business name (consumer brand)." },
    merchantLegalName: { type: "string", description: "Legal operator entity if distinct from consumer brand." },
    date: { type: "string", description: "YYYY-MM-DD." },
    amount: { type: "number", description: "Final total paid." },
    categoryAmounts: {
      type: "array",
      items: {
        type: "object",
        properties: {
          categoryId: { type: "integer" },
          amount: { type: "number" },
        },
        required: ["categoryId", "amount"],
      },
    },
    lineItems: {
      type: "array",
      items: {
        type: "object",
        properties: {
          name: { type: "string", description: "Line-item label as printed on the receipt." },
          price: { type: "number", description: "Pre-tax line total for this item; negative for refunds, coupons, or rewards redemption." },
          categoryId: { type: "integer", description: "Best-fit category for this individual item. Omit for non-item lines (shipping, fees, discounts)." },
        },
        required: ["name", "price"],
      },
    },
    notes: { type: "string" },
  },
  required: ["merchant", "date", "amount"],
};
